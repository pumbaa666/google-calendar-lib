package ch.correvon.google.calendar;

import com.google.api.services.calendar.model.CalendarListEntry;

public class MyCalendarListEntry
{
	public MyCalendarListEntry()
	{
		this(new CalendarListEntry());
	}

	public MyCalendarListEntry(CalendarListEntry calendarListEntry)
	{
		this.calendarListEntry = calendarListEntry;
	}

	@Override public String toString()
	{
		return this.calendarListEntry.getSummary();
	}

	private CalendarListEntry calendarListEntry;
}
