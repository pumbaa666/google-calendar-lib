package ch.correvon.google.calendar.use;

import java.io.File;
import java.util.List;
import javax.swing.SwingWorker;
import com.google.api.services.calendar.model.CalendarListEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ch.correvon.google.calendar.GoogleCalendarService;

public class MainGoogleCalendarLib
{
	private static Log s_logger = LogFactory.getLog(MainGoogleCalendarLib.class);
	private static boolean done = false;
	private static GoogleCalendarService service = null;

	public static void main(String[] args)
	{
		for(String arg:args)
		{
			arg = arg.trim().toLowerCase();
			if(arg.equals("-t") || arg.equals("--test"))
			{
				test();
				continue;
			}

			if(arg.equals("-c") || arg.equals("--clean"))
			{
				deleteCache();
				continue;
			}

			if(arg.equals("-h") || arg.equals("--help"))
			{
				System.out.println("Librairie Java pour Google Calendar API");
				System.out.println("-h, --help\tAffiche l'aide");
				System.out.println("-t, --test\tLance un test automatique afin d'essayer de se connecter à votre agenda");
				System.out.println("-c, --clean\tSupprime le fichier de cache créé suite à une connexion réussie");
				continue;
			}
		}

	}

	private static void deleteCache()
	{
		s_logger.info("Suppression des fichiers de cache");
		
		if(!GoogleCalendarService.DATA_STORE_DIR.exists())
		{	
			s_logger.warn("Le dossier " + GoogleCalendarService.DATA_STORE_DIR.getAbsolutePath() + " n'existe pas");
			return;
		}

		boolean cacheFileExist = (GoogleCalendarService.DATA_STORE_DIR.listFiles().length > 0);
		if(!cacheFileExist)
		{	
			s_logger.warn("Aucun fichier en cache dans " + GoogleCalendarService.DATA_STORE_DIR.getAbsolutePath());
			return;
		}

		try
		{
			for(File file:GoogleCalendarService.DATA_STORE_DIR.listFiles())
			{
				s_logger.info("Suppression de " + file.getAbsolutePath());
				file.delete();
			}
		}
		catch(Exception e)
		{
			s_logger.error(e);
		}
	}

	private static void test()
	{
		connect();

		// Thread qui tourne en boucle et qui attend que la connexion s'établisse (ou échoue)
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				int delay = 1000;
				do
				{
					try
					{
						//						s_logger.debug("sleep "+delay);
						Thread.sleep(delay);
					}
					catch(InterruptedException e)
					{
						e.printStackTrace();
					}
				}
				while(!done);
				s_logger.info("***** terminé *****");
			}
		}).run();
	}

	private static void connect()
	{
		new SwingWorker<CalendarListEntry, CalendarListEntry>()
		{
			@Override
			protected CalendarListEntry doInBackground()
			{
				s_logger.info("Connexion au service google");
				try
				{
					service = GoogleCalendarService.getService();
				}
				catch(Exception e)
				{
					s_logger.fatal(e);
				}

				if(service == null)
				{
					s_logger.error("Impossible de se connecter à l'agenda");
					done = true;
					return null;
				}

				s_logger.info("Récupération des calendriers");
				CalendarListEntry mainCalendar = null;
				List<CalendarListEntry> calendars = null;
				try
				{
					calendars = service.getAllCalendar();
				}
				catch(Exception e)
				{
					s_logger.error(e);
					return null;
				}

				if(calendars.size() <= 0)
					return null;

				for(CalendarListEntry calendar:calendars)
				{
					if(calendar.getPrimary() != null && calendar.getPrimary() == true)
						mainCalendar = calendar;
					publish(calendar); // Va appeler la méthode process du SwingWorker (ci-dessous)
				}

				if(mainCalendar == null)
					mainCalendar = calendars.get(0);
				return mainCalendar;
			}

			@Override
			protected void process(List<CalendarListEntry> calendars) // Appelé (quand cela est possible) par publish.
			{
				// Comme cette méthode n'est pas appelée systematiquement lors de l'appel à publish
				// il faut s'assurer d'entrer toutes les valeurs (calendars est peuplé correctement).
				// En pratique, pour des si petites quantité de calendrier, elle est appellée peu souvent (1x, en fait...),
				for(CalendarListEntry calendar:calendars)
					s_logger.info("- " + calendar.getSummary());
			}

			@Override
			protected void done() // Appelé à la fin de doInBackground
			{
				try
				{
					CalendarListEntry mainCalendar = get(); // Méthode bloquante qui attend la fin de doInBackground et récupère son résultat.
					if(mainCalendar == null)
						s_logger.info("Aucun calendrier trouvé");
					else
						s_logger.info("Calendrier principal : " + mainCalendar.getSummary());
				}
				catch(Exception e)
				{
					s_logger.error(e);
				}
				finally
				{
					done = true;
				}
			}
		}.execute();
	}
}