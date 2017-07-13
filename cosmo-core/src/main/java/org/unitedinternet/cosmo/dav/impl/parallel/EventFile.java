package org.unitedinternet.cosmo.dav.impl.parallel;

import org.apache.jackrabbit.webdav.io.InputContext;
import org.unitedinternet.cosmo.dav.CosmoDavException;
import org.unitedinternet.cosmo.dav.UnprocessableEntityException;
import org.unitedinternet.cosmo.dav.parallel.CalDavContentResource;
import org.unitedinternet.cosmo.dav.parallel.CalDavResource;
import org.unitedinternet.cosmo.dav.parallel.CalDavResourceFactory;
import org.unitedinternet.cosmo.dav.parallel.CalDavResourceLocator;
import org.unitedinternet.cosmo.model.EntityFactory;
import org.unitedinternet.cosmo.model.NoteItem;
import org.unitedinternet.cosmo.model.StampUtils;
import org.unitedinternet.cosmo.model.hibernate.EntityConverter;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.VEvent;

public class EventFile extends CalDavFileBase{

	public EventFile(NoteItem item, 
						CalDavResourceLocator locator,
						CalDavResourceFactory calDavResourceFactory, 
						EntityFactory entityFactory) {
		super(item, locator, calDavResourceFactory, entityFactory);
	}

	public EventFile( CalDavResourceLocator locator,
			CalDavResourceFactory calDavResourceFactory, 
			EntityFactory entityFactory) {
		super(entityFactory.createNote(), locator, calDavResourceFactory, entityFactory);
}
	@Override
	 public Calendar getCalendar() {
        Calendar calendar = new EntityConverter(null).convertNote((NoteItem)getItem());
        // run through client filter because unfortunatley
        // all clients don't adhere to the spec
       // getClientFilterManager().filterCalendar(calendar);
        return calendar;
    }

    public void setCalendar(Calendar calendar) throws CosmoDavException {
            
        ComponentList<VEvent> vevents = calendar.getComponents(Component.VEVENT);
        if (vevents.isEmpty()) {
            throw new UnprocessableEntityException("VCALENDAR does not contain any VEVENTs");
        }

        StampUtils.getEventStamp(getItem()).setEventCalendar(calendar);
    }
    
    public void updateContent(CalDavResource content, InputContext context) throws CosmoDavException {
    	if(!(content instanceof CalDavContentResource)){
    		throw new IllegalArgumentException("Expected instances of  [" + CalDavContentResource.class.getSimpleName() + "]");
    	}
    	
        ((CalDavContentResourceBase)content).populateItem(context);
        updateItem();
    }
}