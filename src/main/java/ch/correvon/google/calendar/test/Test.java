package ch.correvon.google.calendar.test;

import java.util.List;
import javax.swing.SwingWorker;
import com.google.api.services.calendar.model.CalendarListEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ch.correvon.google.calendar.GoogleCalendarService;

public class Test
{
	private static Log s_logger = LogFactory.getLog(Test.class);
	private static boolean done = false;
	private static GoogleCalendarService service = null;
	
	public static void main(String[] args)
	{
		connect();
		
		// Thread qui tourne en boucle et qui attend que la connexion s'établisse (ou échoue)
		new Thread(new Runnable()
		{
			@Override public void run()
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
				List<CalendarListEntry> calendars =  null;
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
					s_logger.info("- "+calendar.getSummary());
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