package org.unitedinternet.cosmo.dav.impl.parallel;

import static org.unitedinternet.cosmo.dav.ExtendedDavConstants.OWNER;
import static org.unitedinternet.cosmo.dav.ExtendedDavConstants.SUPPORTEDREPORTSET;
import static org.unitedinternet.cosmo.dav.ExtendedDavConstants.UUID;
import static org.unitedinternet.cosmo.dav.acl.AclConstants.ACL;
import static org.unitedinternet.cosmo.dav.acl.AclConstants.CURRENTUSERPRIVILEGESET;
import static org.unitedinternet.cosmo.dav.acl.AclConstants.PRINCIPALCOLLECTIONSET;
import static org.unitedinternet.cosmo.dav.ticket.TicketConstants.TICKETDISCOVERY;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

//import javax.xml.namespace.QName;

import org.apache.abdera.i18n.text.UrlEncoding;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyIterator;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameIterator;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.PropEntry;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.version.OptionsResponse;
import org.apache.jackrabbit.webdav.version.report.Report;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.unitedinternet.cosmo.dao.DuplicateItemNameException;
import org.unitedinternet.cosmo.dao.ItemNotFoundException;
import org.unitedinternet.cosmo.dav.ConflictException;
import org.unitedinternet.cosmo.dav.CosmoDavException;
import org.unitedinternet.cosmo.dav.ExistsException;
import org.unitedinternet.cosmo.dav.ForbiddenException;
import org.unitedinternet.cosmo.dav.LockedException;
import org.unitedinternet.cosmo.dav.NotFoundException;
import org.unitedinternet.cosmo.dav.PreconditionFailedException;
import org.unitedinternet.cosmo.dav.ProtectedPropertyModificationException;
import org.unitedinternet.cosmo.dav.UnprocessableEntityException;
import org.unitedinternet.cosmo.dav.acl.DavAcl;
import org.unitedinternet.cosmo.dav.acl.DavPrivilege;
import org.unitedinternet.cosmo.dav.acl.property.Acl;
import org.unitedinternet.cosmo.dav.acl.property.CurrentUserPrivilegeSet;
import org.unitedinternet.cosmo.dav.parallel.CalDavCollection;
import org.unitedinternet.cosmo.dav.parallel.CalDavResource;
import org.unitedinternet.cosmo.dav.property.CreationDate;
import org.unitedinternet.cosmo.dav.property.DisplayName;
import org.unitedinternet.cosmo.dav.property.Etag;
import org.unitedinternet.cosmo.dav.property.IsCollection;
import org.unitedinternet.cosmo.dav.property.LastModified;
import org.unitedinternet.cosmo.dav.property.ResourceType;
import org.unitedinternet.cosmo.dav.property.StandardDavProperty;
import org.unitedinternet.cosmo.dav.property.SupportedReportSet;
import org.unitedinternet.cosmo.dav.property.Uuid;
import org.unitedinternet.cosmo.dav.property.WebDavProperty;
import org.unitedinternet.cosmo.model.Attribute;
import org.unitedinternet.cosmo.model.CollectionItem;
import org.unitedinternet.cosmo.model.CollectionLockedException;
import org.unitedinternet.cosmo.model.DataSizeException;
import org.unitedinternet.cosmo.model.EntityFactory;
import org.unitedinternet.cosmo.model.Item;
import org.unitedinternet.cosmo.model.MessageStamp;
import org.unitedinternet.cosmo.model.NoteItem;
import org.unitedinternet.cosmo.model.QName;
import org.unitedinternet.cosmo.model.Ticket;
import org.unitedinternet.cosmo.model.User;
import org.unitedinternet.cosmo.security.CosmoSecurityManager;
import org.unitedinternet.cosmo.service.ContentService;
import org.unitedinternet.cosmo.util.PathUtil;
import org.w3c.dom.Element;

public abstract class CalDavResourceBase implements CalDavResource {

	private static final HashSet<DavPropertyName> LIVE_PROPERTIES = new HashSet<>(0);
	private static final Set<ReportType> REPORT_TYPES = new HashSet<>(0);
	
	static final Set<String> DEAD_PROPERTY_FILTER = new HashSet<String>();

	static {
		registerLiveProperty(SUPPORTEDREPORTSET);
		registerLiveProperty(ACL);
		registerLiveProperty(CURRENTUSERPRIVILEGESET);
		registerLiveProperty(DavPropertyName.CREATIONDATE);
		registerLiveProperty(DavPropertyName.GETLASTMODIFIED);
		registerLiveProperty(DavPropertyName.GETETAG);
		registerLiveProperty(DavPropertyName.DISPLAYNAME);
		registerLiveProperty(DavPropertyName.ISCOLLECTION);
		registerLiveProperty(DavPropertyName.RESOURCETYPE);
		registerLiveProperty(OWNER);
		registerLiveProperty(PRINCIPALCOLLECTIONSET);
		registerLiveProperty(UUID);
		registerLiveProperty(TICKETDISCOVERY);
		
		 DEAD_PROPERTY_FILTER.add(NoteItem.class.getName());
	     DEAD_PROPERTY_FILTER.add(MessageStamp.class.getName());
	}

	private boolean initialized;
	private DavPropertySet properties;
	private DavAcl acl;
	private CosmoSecurityManager securityManager;
	private ContentService contentService;
	private DavResourceFactory factory;
	private Item item;
	private EntityFactory entityFactory;
	
	protected DavLocatorFactory davLocatorFactory = null;
	 protected DavResourceLocator locator = null;
     protected DavResourceFactory davResourceFactory = null;

	/**
	 * <p>
	 * Registers the name of a live property.
	 * </p>
	 * <p>
	 * Typically used in subclass static initializers to add to the set of live
	 * properties for the resource.
	 * </p>
	 */
	protected static void registerLiveProperty(DavPropertyName name) {
		LIVE_PROPERTIES.add(name);
	}

	@Override
	public String getComplianceClass() {
		return COMPLIANCE_CLASS_SCHEDULING;
	}

	@Override
	public DavResourceLocator getLocator() {
		return null;
	}

	@Override
	public String getResourcePath() {
		return getLocator().getRepositoryPath();
	}

	@Override
	public String getHref() {
		return getLocator().getHref(isCollection());
	}

	@Override
	public void spool(OutputContext outputContext) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public DavPropertyName[] getPropertyNames() {
		loadProperties();
		return properties.getPropertyNames();
	}

	@Override
	public DavProperty<?> getProperty(DavPropertyName name) {
		loadProperties();
		return properties.get(name);
	}

	@Override
	public DavPropertySet getProperties() {
		loadProperties();
		return properties;
	}

	@Override
	public void setProperty(org.apache.jackrabbit.webdav.property.DavProperty<?> property) throws DavException {
		if (!exists()) {
			throw new NotFoundException();
		}
		if (!(property instanceof WebDavProperty)) {
			throw new IllegalArgumentException(
					"Expected type for 'property' is :[" + WebDavProperty.class.getName() + "]");
		}
		setResourceProperty((WebDavProperty) property, false);

		updateItem();
	}

	@Override
	public void removeProperty(DavPropertyName propertyName) throws DavException {
		if (!exists()) {
			throw new NotFoundException();
		}
		removeResourceProperty(propertyName);
		updateItem();
	}

	@Override
	public MultiStatusResponse alterProperties(List<? extends PropEntry> changeList) throws DavException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isLockable(Type type, Scope scope) {
		// nothing is lockable at the moment
		return false;
	}

	@Override
	public boolean hasLock(Type type, Scope scope) {
		// nothing is lockable at the moment
		throw new UnsupportedOperationException();
	}

	@Override
	public ActiveLock getLock(Type type, Scope scope) {
		// nothing is lockable at the moment
		throw new UnsupportedOperationException();
	}

	@Override
	public ActiveLock[] getLocks() {
		// nothing is lockable at the moment
		throw new UnsupportedOperationException();
	}

	@Override
	public ActiveLock lock(LockInfo reqLockInfo) throws DavException {
		// nothing is lockable at the moment
		throw new PreconditionFailedException("Resource not lockable");
	}

	@Override
	public ActiveLock refreshLock(LockInfo reqLockInfo, String lockToken) throws DavException {
		// nothing is lockable at the moment
		throw new PreconditionFailedException("Resource not lockable");
	}

	@Override
	public void unlock(String lockToken) throws DavException {
		// nothing is lockable at the moment
		throw new PreconditionFailedException("Resource not lockable");
	}

	@Override
	public void addLockManager(LockManager lockmgr) {
		// nothing is lockable at the moment
		throw new UnsupportedOperationException();
	}

	@Override
	public String getETag() {
		if (getItem() == null)
			return null;
		// an item that is about to be created does not yet have an etag
		if (StringUtils.isBlank(getItem().getEntityTag())) {
			return null;
		}
		return "\"" + getItem().getEntityTag() + "\"";
	}

	@Override
	public long getModificationTime() {
		if (getItem() == null)
			return -1;
		if (getItem().getModifiedDate() == null)
			return new Date().getTime();
		return getItem().getModifiedDate().getTime();
	}

	@Override
	public DavResource getCollection() {
		return getParent();

	}

	public void move(DavResource destination) throws DavException {
		if (!exists()) {
			throw new NotFoundException();
		}

		if (destination.exists()) {
			throw new ExistsException();
		}

		if (!(destination instanceof CalDavResourceBase)) {
			throw new IllegalArgumentException(
					"Required type for 'destination' is:[" + CalDavResourceBase.class.getName() + "]");
		}
		CalDavResourceBase destinationItemResource = (CalDavResourceBase) destination;

		try {

			CalDavResourceBase parentItemResource = (CalDavResourceBase) destinationItemResource.getParent();

			CollectionItem newParent = (CollectionItem) parentItemResource.getItem();

			if (!parentItemResource.exists() || newParent == null) {
				throw new ConflictException("One or more intermediate collections must be created");
			}
			CollectionItem oldParent = (CollectionItem) getParent().getItem();

			// update name
			getItem().setName(PathUtil.getBasename(destination.getResourcePath()));

			// only move if parents are different
			if (!newParent.equals(oldParent)) {
				getContentService().moveItem(getItem(), oldParent, newParent);
			} else {
				// otherwise update name
				updateItem();
			}

		} catch (ItemNotFoundException e) {
			throw new ConflictException("One or more intermediate collections must be created");
		} catch (DuplicateItemNameException e) {
			throw new ExistsException();
		} catch (CollectionLockedException e) {
			throw new LockedException();
		}
	}

	public void copy(DavResource destination, boolean shallow) throws DavException {
		if (!exists()) {
			throw new NotFoundException();
		}

		try {
			getContentService().copyItem(getItem(),
					(CollectionItem) ((CalDavCollection) destination.getCollection()).getItem(),
					destination.getResourcePath(), !shallow);
		} catch (ItemNotFoundException e) {
			throw new ConflictException("One or more intermediate collections must be created");
		} catch (DuplicateItemNameException e) {
			throw new ExistsException();
		} catch (CollectionLockedException e) {
			throw new LockedException();
		}
	}

	public MultiStatusResponse updateProperties(DavPropertySet setProperties, DavPropertyNameSet removePropertyNames)
			throws CosmoDavException {
		if (!exists()) {
			throw new NotFoundException();
		}

		MultiStatusResponse msr = new MultiStatusResponse(getHref(), null);

		ArrayList<DavPropertyName> df = new ArrayList<DavPropertyName>();
		CosmoDavException error = null;
		DavPropertyName failed = null;

		DavProperty<?> property = null;
		for (DavPropertyIterator i = setProperties.iterator(); i.hasNext();) {
			try {
				property = i.nextProperty();
				setResourceProperty((WebDavProperty) property, false);
				df.add(property.getName());
				msr.add(property.getName(), 200);
			} catch (CosmoDavException e) {
				// we can only report one error message in the
				// responsedescription, so even if multiple properties would
				// fail, we return 424 for the second and subsequent failures
				// as well
				if (error == null) {
					error = e;
					failed = property.getName();
				} else {
					df.add(property.getName());
				}
			}
		}

		DavPropertyName name = null;
		for (DavPropertyNameIterator i = removePropertyNames.iterator(); i.hasNext();) {
			try {
				name = (DavPropertyName) i.next();
				removeResourceProperty(name);
				df.add(name);
				msr.add(name, 200);
			} catch (CosmoDavException e) {
				// we can only report one error message in the
				// responsedescription, so even if multiple properties would
				// fail, we return 424 for the second and subsequent failures
				// as well
				if (error == null) {
					error = e;
					failed = name;
				} else {
					df.add(name);
				}
			}
		}

		if (error != null) {
			// replace the other response with a new one, since we have to
			// change the response code for each of the properties that would
			// have been set successfully
			msr = new MultiStatusResponse(getHref(), error.getMessage());
			for (DavPropertyName n : df) {
				msr.add(n, 424);
			}
			msr.add(failed, error.getErrorCode());
		}

		if (hasNonOK(msr)) {
			return msr;
		}

		updateItem();

		return msr;
	}

	public static boolean hasNonOK(MultiStatusResponse msr) {
		if (msr == null || msr.getStatus() == null) {
			return false;
		}

		for (Status status : msr.getStatus()) {

			if (status != null) {
				int statusCode = status.getStatusCode();

				if (statusCode != 200) {
					return true;
				}
			}
		}
		return false;
	}

	public void saveTicket(Ticket ticket) throws CosmoDavException {

		// automatically add freebusy privilege along with read
		if (ticket.getPrivileges().contains(Ticket.PRIVILEGE_READ))
			ticket.getPrivileges().add(Ticket.PRIVILEGE_FREEBUSY);

		getContentService().createTicket(getItem(), ticket);
	}

	public void removeTicket(Ticket ticket) throws CosmoDavException {
		getContentService().removeTicket(getItem(), ticket);
	}

	public Ticket getTicket(String id) {
		for (Iterator<Ticket> i = getItem().getTickets().iterator(); i.hasNext();) {
			Ticket t = (Ticket) i.next();
			if (t.getKey().equals(id)) {
				return t;
			}
		}
		return null;
	}

	public Set<Ticket> getTickets() {
		return getSecurityManager().getSecurityContext().findVisibleTickets(getItem());
	}

	// Template methods for CalDAV resources
	/**
	 * Sets the properties of the item backing this resource from the given
	 * input context.
	 */
	protected void populateItem(InputContext inputContext) throws CosmoDavException {

		Item item = getItem();
		if (item != null && item.getUid() == null) {
			try {
				getItem().setName(UrlEncoding.decode(PathUtil.getBasename(getResourcePath()), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new CosmoDavException(e);
			}
			if (item.getDisplayName() == null) {
				item.setDisplayName(item.getName());
			}
		}

		// if we don't know specifically who the user is, then the
		// owner of the resource becomes the person who issued the
		// ticket

		// Only initialize owner once
		if (item.getOwner() == null) {
			User owner = getSecurityManager().getSecurityContext().getUser();
			if (owner == null) {
				Ticket ticket = getSecurityManager().getSecurityContext().getTicket();
				owner = ticket.getOwner();
			}
			item.setOwner(owner);
		}

		if (item.getUid() == null) {
			item.setClientCreationDate(Calendar.getInstance().getTime());
			item.setClientModifiedDate(item.getClientCreationDate());
		}
	}

	/**
	 * Sets the attributes the item backing this resource from the given
	 * property set.
	 */
	protected MultiStatusResponse populateAttributes(DavPropertySet properties) {

		MultiStatusResponse msr = new MultiStatusResponse(getHref(), null);
		if (properties == null) {
			return msr;
		}

		org.apache.jackrabbit.webdav.property.DavProperty<?> property = null;
		List<DavPropertyName> df = new ArrayList<DavPropertyName>();
		CosmoDavException error = null;
		DavPropertyName failed = null;
		for (DavPropertyIterator i = properties.iterator(); i.hasNext();) {
			try {
				property = i.nextProperty();
				setResourceProperty((WebDavProperty) property, true);
				df.add(property.getName());
				msr.add(property.getName(), 200);
			} catch (CosmoDavException e) {
				// we can only report one error message in the
				// responsedescription, so even if multiple properties would
				// fail, we return 424 for the second and subsequent failures
				// as well
				if (error == null) {
					error = e;
					failed = property.getName();
				} else {
					df.add(property.getName());
				}
			}
		}

		if (error == null) {
			return msr;
		}

		// replace the other response with a new one, since we have to
		// change the response code for each of the properties that would
		// have been set successfully
		msr = new MultiStatusResponse(getHref(), error.getMessage());
		for (DavPropertyName n : df)
			msr.add(n, 424);
		msr.add(failed, error.getErrorCode());

		return msr;
	}

	 protected void removeLiveProperty(DavPropertyName name)
	            throws CosmoDavException {
	        if (getItem() == null) {
	            return;
	        }

	        if (name.equals(DavPropertyName.CREATIONDATE)
	                || name.equals(DavPropertyName.GETLASTMODIFIED)
	                || name.equals(DavPropertyName.GETETAG)
	                || name.equals(DavPropertyName.DISPLAYNAME)
	                || name.equals(DavPropertyName.RESOURCETYPE)
	                || name.equals(DavPropertyName.ISCOLLECTION)
	                || name.equals(OWNER) || name.equals(PRINCIPALCOLLECTIONSET)
	                || name.equals(TICKETDISCOVERY) || name.equals(UUID)) {
	            throw new ProtectedPropertyModificationException(name);
	        }

	        getProperties().remove(name);
	    }
	 

	protected abstract void updateItem() throws CosmoDavException;

	protected ContentService getContentService(){
		return contentService;
	}

	/**
	 * Sets a live DAV property on the resource on resource initialization.
	 *
	 * @param property
	 *            the property to set
	 *
	 * @throws CosmoDavException
	 *             if the property is protected or if a null value is specified
	 *             for a property that does not accept them or if an invalid
	 *             value is specified
	 */
	 protected void setLiveProperty(WebDavProperty property, boolean create)
	            throws CosmoDavException {
		 	Item item = getItem();
	        if (item == null) {
	            return;
	        }

	        DavPropertyName name = property.getName();
	        if (property.getValue() == null) {
	            throw new UnprocessableEntityException("Property " + name
	                    + " requires a value");
	        }

	        if (name.equals(DavPropertyName.CREATIONDATE)
	                || name.equals(DavPropertyName.GETLASTMODIFIED)
	                || name.equals(DavPropertyName.GETETAG)
	                || name.equals(DavPropertyName.RESOURCETYPE)
	                || name.equals(DavPropertyName.ISCOLLECTION)
	                || name.equals(OWNER) || name.equals(PRINCIPALCOLLECTIONSET)
	                || name.equals(TICKETDISCOVERY) || name.equals(UUID)) {
	            throw new ProtectedPropertyModificationException(name);
	        }

	        if (name.equals(DavPropertyName.DISPLAYNAME)) {
	            item.setDisplayName(property.getValueText());
	        }
	    }

	/**
	 * Calls {@link #setLiveProperty(WebDavProperty)} or
	 * {@link setDeadProperty(WebDavProperty)}.
	 */
	protected void setResourceProperty(WebDavProperty property, boolean create) throws CosmoDavException {
		DavPropertyName name = property.getName();
		if (name.equals(SUPPORTEDREPORTSET)) {
			throw new ProtectedPropertyModificationException(name);
		}

		if (isLiveProperty(property.getName())) {
			setLiveProperty(property, create);
		} else {
			setDeadProperty(property);
		}

		properties.add(property);
	}

	/**
	 * Calls {@link #removeLiveProperty(DavPropertyName)} or
	 * {@link removeDeadProperty(DavPropertyName)}.
	 */
	protected void removeResourceProperty(DavPropertyName name) throws CosmoDavException {
		if (name.equals(SUPPORTEDREPORTSET)) {
			throw new ProtectedPropertyModificationException(name);
		}

		if (isLiveProperty(name)) {
			removeLiveProperty(name);
		} else {
			removeDeadProperty(name);
		}

		properties.remove(name);
	}

	protected Set<ReportType> getReportTypes() {
		return REPORT_TYPES;
	}

	/**
	 * <p>
	 * Returns the set of privileges granted on the resource to the current
	 * principal.
	 * </p>
	 * <p>
	 * If the request is unauthenticated, returns an empty set. If the current
	 * principal is an admin user, returns {@link DavPrivilege#ALL}.
	 * </p>
	 */
	protected CosmoSecurityManager getSecurityManager(){
		return securityManager;
	}

	/**
	 * <p>
	 * Extends the superclass method.
	 * </p>
	 * <p>
	 * If the principal is a user, returns {@link DavPrivilege#READ} and
	 * {@link DavPrivilege@WRITE}. This is a shortcut that assumes the security
	 * layer has only allowed access to the owner of the home collection
	 * specified in the URL used to access this resource. Eventually this method
	 * will check the ACL for all ACEs corresponding to the current principal
	 * and return the privileges those ACEs grant.
	 * </p>
	 * <p>
	 * If the principal is a ticket, returns the dav privileges corresponding to
	 * the ticket's privileges, since a ticket is in effect its own ACE.
	 * </p>
	 */
	protected Set<DavPrivilege> getCurrentPrincipalPrivileges() {
		HashSet<DavPrivilege> privileges = new HashSet<DavPrivilege>();

		// all privileges are implied for admin users
		if (getSecurityManager().getSecurityContext().isAdmin()) {
			privileges.add(DavPrivilege.ALL);
		}

		// XXX eventually we will want to find the aces for the user and
		// add each of their granted privileges
		User user = getSecurityManager().getSecurityContext().getUser();
		if (user != null) {
			privileges.add(DavPrivilege.READ);
			privileges.add(DavPrivilege.WRITE);
			privileges.add(DavPrivilege.READ_CURRENT_USER_PRIVILEGE_SET);
			privileges.add(DavPrivilege.READ_FREE_BUSY);
			return privileges;
		}

		Ticket ticket = getSecurityManager().getSecurityContext().getTicket();
		if (ticket != null) {
			privileges.add(DavPrivilege.READ_CURRENT_USER_PRIVILEGE_SET);

			if (ticket.getPrivileges().contains(Ticket.PRIVILEGE_READ)) {
				privileges.add(DavPrivilege.READ);
			}
			if (ticket.getPrivileges().contains(Ticket.PRIVILEGE_WRITE)) {
				privileges.add(DavPrivilege.WRITE);
			}
			if (ticket.getPrivileges().contains(Ticket.PRIVILEGE_FREEBUSY)) {
				privileges.add(DavPrivilege.READ_FREE_BUSY);
			}

			return privileges;
		}

		return privileges;
	}

	protected  DavAcl getAcl(){
		return acl;
	}
	 /**
     * Returns the set of resource types for this resource.
     */
	
	protected void loadLiveProperties(DavPropertySet properties) {
		Item item = getItem();
        if (item == null) {
            return;
        }

        properties.add(new CreationDate(item.getCreationDate()));
        properties.add(new LastModified(item.getModifiedDate()));
        properties.add(new Etag(getETag()));
        properties.add(new DisplayName(getDisplayName()));
        properties.add(new ResourceType(getResourceTypes()));
        properties.add(new IsCollection(isCollection()));
        //TODO: change the way commented objects are created
       /* properties.add(new Owner(getResourceLocator(), item.getOwner()));
        properties.add(new PrincipalCollectionSet(getResourceLocator()));
        properties.add(new TicketDiscovery(getResourceLocator(), getTickets()));*/
        properties.add(new Uuid(item.getUid()));
    }

	/**
     * Returns a list of names of <code>Attribute</code>s that should not be
     * exposed through DAV as dead properties.
     */
    protected abstract Set<String> getDeadPropertyFilter();
    
    /**
     * Returns the set of resource types for this resource.
     */
    protected abstract Set<javax.xml.namespace.QName> getResourceTypes();
    
    protected void loadDeadProperties(DavPropertySet properties) {
        for (Iterator<Map.Entry<QName, Attribute>> i = getItem().getAttributes().entrySet().iterator(); i.hasNext();) {
            Map.Entry<QName, Attribute> entry = i.next();

            // skip attributes that are not meant to be shown as dead
            // properties
            if (getDeadPropertyFilter().contains(entry.getKey().getNamespace())) {
                continue;
            }

            DavPropertyName propName = qNameToPropName(entry.getKey());

            // ignore live properties, as they'll be loaded separately
            if (isLiveProperty(propName)) {
                continue;
            }

            // XXX: language
            Object propValue = entry.getValue().getValue();
            properties.add(new StandardDavProperty(propName, propValue, false));
        }
    }
    private DavPropertyName qNameToPropName(QName qname) {
        // no namespace at all
        if ("".equals(qname.getNamespace())) {
            return DavPropertyName.create(qname.getLocalName());
        }

        Namespace ns = Namespace.getNamespace(qname.getNamespace());

        return DavPropertyName.create(qname.getLocalName(), ns);
    }
	protected void loadProperties() {
		if (initialized) {
			return;
		}

		properties.add(new SupportedReportSet(getReportTypes()));
		properties.add(new Acl(getAcl()));
		properties.add(new CurrentUserPrivilegeSet(getCurrentPrincipalPrivileges()));

		loadLiveProperties(properties);
		loadDeadProperties(properties);

		initialized = true;
	}

	/**
	 * Determines whether or not the given property name identifies a live
	 * property.
	 * 
	 * If the server understands the semantic meaning of a property (probably
	 * because the property is defined in a DAV-related specification
	 * somewhere), then the property is defined as "live". Live properties are
	 * typically explicitly represented in the object model.
	 *
	 * If the server does not know anything specific about the property (usually
	 * because it was defined by a particular client), then it is known as a
	 * "dead" property.
	 */
	protected boolean isLiveProperty(DavPropertyName name) {
		return LIVE_PROPERTIES.contains(name);
	}
	
	@Override
	public DavSession getSession() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DavResourceFactory getFactory() {
		return factory;
	}
	
    public void removeMember(DavResource member) throws DavException {
            throw new UnsupportedOperationException();
     }
    
    public DavResourceIterator getMembers() {
        // while it would be ideal to throw an UnsupportedOperationException,
        // MultiStatus tries to add a MultiStatusResponse for every member
        // of a WebDavResource regardless of whether or not it's a collection,
        // so we need to return an empty iterator.
        return new DavResourceIteratorImpl(new ArrayList<DavResource>());
    }
    
    public void addMember(DavResource member, InputContext inputContext)throws DavException {
    	throw new UnsupportedOperationException();
    }
    
    public String getDisplayName() {
        return getItem().getDisplayName();
    }
    
    public boolean exists() {
        return getItem()!= null && getItem().getUid() != null;
    }
    
    @Override
    public DavResource[] getReferenceResources(DavPropertyName hrefPropertyName) {
    	return new DavResource[]{};
    }
    
    @Override
    public void addWorkspace(org.apache.jackrabbit.webdav.DavResource workspace) {
    	
    }
    
    public Report getReport(ReportInfo reportInfo)
            throws CosmoDavException {
            if (! exists()) {
                throw new NotFoundException();
            }

            if (! isSupportedReport(reportInfo)) {
                throw new UnprocessableEntityException("Unknown report " + reportInfo.getReportName());
            }

            try {
            	return ReportType.getType(reportInfo).createReport(this, reportInfo);
            } catch (DavException e){
                if (e instanceof CosmoDavException) {
                    throw (CosmoDavException) e;
                }
                throw new CosmoDavException(e);
            }
        }
    
    protected boolean isSupportedReport(ReportInfo info) {
        for (Iterator<ReportType> i=getReportTypes().iterator(); i.hasNext();) {
            if (i.next().isRequestedReportType(info)) {
                return true;
            }
        }
        return false;
    }
 
    protected void removeDeadProperty(DavPropertyName name) throws CosmoDavException {

        getItem().removeAttribute(propNameToQName(name));
    }
 
    protected void setDeadProperty(WebDavProperty property)
            throws CosmoDavException {
        

        if (property.getValue() == null) {
            throw new UnprocessableEntityException("Property "
                    + property.getName() + " requires a value");
        }

        try {
        	org.unitedinternet.cosmo.model.QName qname = propNameToQName(property.getName());
            Element value = (Element) property.getValue();
            Attribute attr = getItem().getAttribute(qname);

            // first check for existing attribute otherwise add
            if (attr != null) {
                attr.setValue(value);
            } else {
                getItem().addAttribute(entityFactory.createXMLAttribute(qname, value));
            }
        } catch (DataSizeException e) {
            throw new ForbiddenException(e.getMessage());
        }
    }

    
    protected org.unitedinternet.cosmo.model.QName propNameToQName(DavPropertyName name) {
        if (name == null) {
            final String msg = "name cannot be null";
            throw new IllegalArgumentException(msg);
        }

        Namespace ns = name.getNamespace();
        String uri = ns != null ? ns.getURI() : "";

        return entityFactory.createQName(uri, name.getName());
    }
    
    public OptionsResponse getOptionResponse(OptionsInfo optionsInfo){
    	return null;
    }
    
    public void setItem(Item item) throws CosmoDavException {
        this.item = item;
        loadProperties();
    }
    
    public Item getItem() {
        return item;
    }

	public EntityFactory getEntityFactory() {
		return entityFactory;
	}
}