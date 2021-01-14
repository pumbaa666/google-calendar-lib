package ch.correvon.google.calendar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Source : https://developers.google.com/google-apps/calendar/quickstart/java
 * 
 * API Reference : https://developers.google.com/google-apps/calendar/v3/reference/
 * 
 * @author Loic Correvon
 */
public class GoogleCalendarService
{
	public static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/pumbaa-google-calendar-lib");
	private static FileDataStoreFactory DATA_STORE_FACTORY;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static HttpTransport HTTP_TRANSPORT;

	public static final String VERSION = "1.0.0";

	/**
	 * Global instance of the scopes required by this quickstart
	 * 
	 * Ajouter le scope "https://www.googleapis.com/auth/calendar"
	 * Source : https://developers.google.com/google-apps/calendar/create-events
	 * 
	 * En cas de problème "Insufficient permissions" malgré le scope "https://www.googleapis.com/auth/calendar",
	 * supprimer le fichier StoredCredential (Dans DATA_STORE_DIR)
	 * Source : http://stackoverflow.com/questions/30293293/insufficient-permissions-on-google-calendar-apis-acl-list
	 */
	private static final List<String> SCOPES = Arrays.asList(new String[]{"https://www.googleapis.com/auth/calendar"/*, CalendarScopes.CALENDAR*/});
	private static Log s_logger = LogFactory.getLog(GoogleCalendarService.class);

	static
	{
		try
		{
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		}
		catch(/*IOException | GeneralSecurityException*/ Exception e)
		{
			s_logger.error(e);
			System.exit(1);
		}
	}
	
	public static String getVersion()
	{
		return VERSION;
	}
	
	/* ----------------------------------------------------------------------------- *\
	|*                                    INIT                                       *|
	\* ----------------------------------------------------------------------------- */
	/**
	 * Retourne une instance de GoogleCalendarService si l'initialisation est un succès.
	 * Retourne null sinon.
	 * 
	 * @param calendarName Le nom du calendrier sélectionné
	 * @param createIfNotExist Créer le calendrier si il n'existe pas
	 * 
	 * @return une instance de GoogleCalendarService, null sinon.
	 */
	public static GoogleCalendarService getService()
	{
		GoogleCalendarService gcService = new GoogleCalendarService();

		if(gcService.service == null)
			return null;

		return gcService;
	}

	/**
	 * Constructeur privé.
	 * Il n'est pas public car l'initialisation peut échouer et retourner null.
	 * 
	 * @param calendarName
	 * @param createIfNotExist
	 */
	private GoogleCalendarService()
	{
		this.service = this.getCalendarService();
		if(this.service == null)
			return;
	}

	/**
	 * Construit et retourne un service client Google.
	 * 
	 * @return Un service client authorisé.
	 */
	private Calendar getCalendarService()
	{
		Credential credential = authorize();
		if(credential == null)
			return null;

		return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName("pumbaa-google-calendar-lib").build();
	}

	/**
	 * Creates an authorized Credential object.
	 * 
	 * @return an authorized Credential object.
	 */
	private Credential authorize()
	{
		InputStream in = GoogleCalendarService.class.getResourceAsStream("/oauth-key.json");

		try
		{
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

			// Vérifier l'existence du fichier de cache avant d'appeler GoogleAuthorizationCodeFlow.Builder.setDataStoreFactory
			// car cette méthode crée un fichier minimal.
			boolean cacheFileExist = (DATA_STORE_DIR.listFiles().length > 0);

			// Build flow and trigger user authorization request.
			GoogleAuthorizationCodeFlow.Builder builder = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES);
			builder.setDataStoreFactory(DATA_STORE_FACTORY);
			builder.setAccessType("offline");
			GoogleAuthorizationCodeFlow flow = builder.build();

			System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
			Credential credential = null;
			try
			{
				credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
			}
			catch(/*TokenResponse*/Exception e)
			{
				s_logger.fatal(e);
				return null;
			}
			
			
			if(credential == null || credential.getAccessToken() == null || credential.getAccessToken().isEmpty()) // Le token est vide
			{
				if(DATA_STORE_DIR.exists() && cacheFileExist)
				{
					// Si le token stocké dans DATA_STORE_DIR est corrompu ou que l'utilisateur a révoqué les permissions
					// la méthode AuthorizationCodeInstalledApp.authorize retourne un Credential non null mais dont le token est vide.
					// Il faut donc supprimer le fichier en cache et redemander une autorisation.
					// Seulement on doit créer une nouvelle instance de GoogleNetHttpTransport et DATA_STORE_FACTORY pour qu'elle soit prise en compte.
					DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);

					for(File file:DATA_STORE_DIR.listFiles())
						file.delete();
					credential = authorize();
				}
			}

			return credential;
		}
		catch(IOException e)
		{
			s_logger.error("Erreur lors de l'initialisation", e);
			return null;
		}
	}

	public String selectCalendar(String calendarName)
	{
		return this.selectCalendar(calendarName, null);
	}

	/**
	 * Sélectionne un calendrier.
	 * 
	 * @param calendarName Nom du calendrier à sélectionner.
	 * 
	 * @return L'id du calendrier si il existe. Null sinon.
	 */
	public String selectCalendar(String calendarName, List<CalendarListEntry> calendars)
	{
		if(calendarName.trim().isEmpty())
			return null;

		this.calendarId = this.getCalendar(calendarName, calendars);
		if(this.calendarId == null)
		{
			s_logger.error("Impossible de trouver l'agenda \"" + calendarName + "\"");
			return null;
		}

		s_logger.debug("Sélection de l'agenda \"" + calendarName + "\" : " + calendarId);
		return this.calendarId;
	}

	/* ----------------------------------------------------------------------------- *\
	|*                                  CALENDAR                                     *|
	\* ----------------------------------------------------------------------------- */

	/**
	 * Retourne tous les calendriers visibles.
	 * 
	 * Source : https://developers.google.com/google-apps/calendar/v3/reference/
	 * 
	 * @param service Connection au service Google.
	 * 
	 * @return Liste des calendriers visibles.
	 */
	public List<CalendarListEntry> getAllCalendar()
	{
		List<CalendarListEntry> allCalendar = new ArrayList<>(10);

		s_logger.debug("Agendas visibles : ");
		String pageToken = null;
		do
		{
			CalendarList calendarList;
			try
			{
				calendarList = this.service.calendarList().list().setPageToken(pageToken).execute();
			}
			catch(Exception e)
			{
				s_logger.error("Impossible d'obtenir la liste des calendrier", e);
				return null;
			}
			List<CalendarListEntry> items = calendarList.getItems();

			for(CalendarListEntry item:items)
			{
				allCalendar.add(item);
				s_logger.debug(item.getSummary() + " (" + item.getAccessRole() + ")");
			}
			pageToken = calendarList.getNextPageToken();
		}
		while(pageToken != null);

		return allCalendar;
	}

	public String getCalendar(String calendarName)
	{
		return this.getCalendar(calendarName, null);
	}

	/**
	 * Retourne l'ID du calendrier dont le nom est passé en paramètre.
	 * 
	 * @param calendarName Nom du calendrier à chercher
	 * 
	 * @return l'id du calendrier si il existe. Null sinon.
	 */
	public String getCalendar(String calendarName, List<CalendarListEntry> calendars)
	{
		List<CalendarListEntry> items;
		if(calendars == null)
		{
			items = this.getAllCalendar();
			if(items == null)
				return null;
		}
		else
			items = calendars;

		for(CalendarListEntry item:items)
			if(item.getSummary().equalsIgnoreCase(calendarName))
				return item.getId();

		return null;
	}

	/**
	 * Créer un calendrier.
	 * 
	 * @param calendarName Nom du calendrier à créer.
	 * 
	 * @return L'id du nouveau calendrier si il a été créé avec succès. Null sinon.
	 */
	public String createCalendar(String calendarName)
	{
		com.google.api.services.calendar.model.Calendar newCalendar = new com.google.api.services.calendar.model.Calendar();
		newCalendar.setSummary(calendarName);
//		calendar.setTimeZone("Europe/Zurich");

		try
		{
			newCalendar = this.service.calendars().insert(newCalendar).execute();
			s_logger.info("Agenda créé : " + newCalendar.getSummary() + " (" + newCalendar.getId() + ")");
		}
		catch(IOException e)
		{
			s_logger.error("Impossible de créer le calendrier " + calendarName, e);
			return null;
		}

		return newCalendar.getId();
	}

	/**
	 * Supprime un calendrier.
	 * 
	 * @param calendarId L'id du calendrier à supprimer.
	 */
	public void deleteCalendar(String calendarId)
	{
		try
		{
			com.google.api.services.calendar.model.Calendar calendar = this.service.calendars().get(calendarId).execute();

			this.service.calendars().delete(calendarId).execute();
			s_logger.info("Agenda supprimé " + calendar.getSummary() + " (" + calendarId + ")");
		}
		catch(IOException e)
		{
			s_logger.error("Impossible de supprimer le calendrier " + calendarId, e);
		}
	}

	/* ----------------------------------------------------------------------------- *\
	|*                                    EVENT                                      *|
	\* ----------------------------------------------------------------------------- */
	public List<Event> getNextEvents()
	{
		return this.getNextEvents(-1, null, null, null);
	}

	/**
	 * Retourne les nbEvent prochains événements de l'agenda sélectionné.
	 * 
	 * https://developers.google.com/google-apps/calendar/quickstart/java
	 * 
	 * @param nbEvent Nombre d'événement maximum à retourner.
	 */
	public List<Event> getNextEvents(int nbEvent, Date minDate, Date maxDate, String filter)
	{
		DateTime now = new DateTime(System.currentTimeMillis());

		Events events;
		Calendar.Events.List list;
		try
		{
			list = this.service.events().list(this.calendarId);
			if(nbEvent > 0)
				list.setMaxResults(nbEvent);

			if(minDate == null)
				list.setTimeMin(now);
			else
				list.setTimeMin(new DateTime(minDate.getTime()));

			if(maxDate != null)
				list.setTimeMax(new DateTime(maxDate.getTime()));
			
			list.setOrderBy("startTime");
			list.setSingleEvents(true);
			
//			https://stackoverflow.com/questions/22977955/how-to-use-setq-method-in-google-calendar-api-v3-java/23801583
//			https://googleapis.dev/java/google-api-services-calendar/latest/index.html
//			if(filter != null && !filter.trim().isEmpty())
//				list.setQ(filter); // TODO pk ça marche pas ??

			events = list.execute();
		}
		catch(IOException e)
		{
			s_logger.error("Impossible d'obtenir les événement de l'agenda \"" + this.calendarId + "\"", e);
			return null;
		}

		List<Event> items = events.getItems();
		if(items.size() == 0)
			s_logger.debug("Aucun évenements à venir");
		else
		{
			s_logger.debug("Évenements à venir (" + nbEvent + ") : ");
			for(Event event:items)
			{
				DateTime start = event.getStart().getDateTime();
				if(start == null)
					start = event.getStart().getDate();
				s_logger.debug(event.getSummary() + " (" + event.getId() + ") " + start);
			}
		}

		return items;
	}

	/**
	 * Créer un évenement.
	 * 
	 * @param name Nom.
	 * @param description Description.
	 * @param location. Localisaiton.
	 * @param start Date et heure de début.
	 * @param end Date et heure de fin.
	 * 
	 * @return L'id de l'évenement si il a été créé avec succès. Null sinon.
	 */
	public String createEvent(String name, String description, String type, String location, Date start, Date end)
	{
		return this.createEvent(name, description, type, location, start, end, null, null, null);
	}

	/**
	 * Créer un évenement.
	 * Source : https://developers.google.com/google-apps/calendar/create-events
	 * 
	 * @param name Nom.
	 * @param description Description.
	 * @param location. Localisaiton.
	 * @param start Date et heure de début.
	 * @param end Date et heure de fin.
	 * @param timezone Fuseau horaire. Ex : "Europe/Zurich" ou "America/Los_Angeles"
	 * @param recurrence Tableau des répétition. Ex pour répéter l'évenement tous les jours, maximum 2 fois : "RRULE:FREQ=DAILY;COUNT=2"
	 * @param attendeeEmails Tableau d'email des invités.
	 * 
	 * @return L'id de l'évenement si il a été créé avec succès. Null sinon.
	 */
	public String createEvent(String name, String description, String type, String location, Date start, Date end, String timezone, String[] recurrence, String[] attendeeEmails)
	{
		if(start == null || end == null)
		{
			s_logger.error("Veuillez choisir une date de début et de fin");
			return null;
		}

		Event event = new Event();
		event.setSummary(name);
		event.setLocation(location);
		event.setDescription(description + "\n" + "(" + type + ")");

		EventDateTime eventStart = new EventDateTime();
		eventStart.setDateTime(new DateTime(start));
		event.setStart(eventStart);

		EventDateTime eventEnd = new EventDateTime();
		eventEnd.setDateTime(new DateTime(end));
		event.setEnd(eventEnd);
		
		//event.setStatus(type);

		if(timezone != null && !timezone.trim().isEmpty())
		{
			eventStart.setTimeZone(timezone);
			eventEnd.setTimeZone(timezone);
		}

		if(recurrence != null && recurrence.length > 0)
			event.setRecurrence(Arrays.asList(recurrence));

		if(attendeeEmails != null && attendeeEmails.length > 0)
		{
			List<EventAttendee> attendees = new ArrayList<>(attendeeEmails.length);
			for(String attendeeEmail:attendeeEmails)
			{
				EventAttendee attendee = new EventAttendee();
				attendee.setEmail(attendeeEmail);
				attendees.add(attendee);
			}
			event.setAttendees(attendees);
		}

		try
		{
			event = this.service.events().insert(this.calendarId, event).execute();
		}
		catch(IOException e)
		{
			s_logger.error("Impossible de créer un nouvel évenement dans l'agenda \"" + this.calendarId + "\"", e);
			return null;
		}

		s_logger.info("Événement créé : " + event.getSummary() + "(" + event.getId() + ") " + event.getHtmlLink());

		return event.getId();
	}

	/**
	 * Suprrime un évenement.
	 * 
	 * @param eventId L'id de l'évenement à supprimer.
	 */
	public void deleteEvent(String eventId)
	{
		try
		{
			Event event = this.service.events().get(this.calendarId, eventId).execute(); // Utile uniquement si on veut le nom de l'event avant de le supprimer.

			this.service.events().delete(this.calendarId, eventId).execute();
			s_logger.info("Événement supprimé : " + event.getSummary() + " (" + eventId + ")");
		}
		catch(Exception e)
		{
			s_logger.error("Impossible de supprimer l'évenement \"" + eventId + "\" dans l'agenda \"" + this.calendarId + "\"", e);
			return;
		}
	}

	private Calendar service;
	private String calendarId;
}
