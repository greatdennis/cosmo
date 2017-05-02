package org.unitedinternet.cosmo.dav.impl.parallel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.unitedinternet.cosmo.CosmoException;
import org.unitedinternet.cosmo.dav.CosmoDavException;
import org.unitedinternet.cosmo.dav.DavCollection;
import org.unitedinternet.cosmo.dav.DavContent;
import org.unitedinternet.cosmo.dav.ExtendedDavConstants;
import org.unitedinternet.cosmo.dav.LockedException;
import org.unitedinternet.cosmo.dav.WebDavResource;
import org.unitedinternet.cosmo.dav.impl.DavCollectionBase;
import org.unitedinternet.cosmo.dav.impl.DavContentBase;
import org.unitedinternet.cosmo.dav.impl.DavItemCollection;
import org.unitedinternet.cosmo.dav.impl.DavItemContent;
import org.unitedinternet.cosmo.dav.parallel.CalDavCollection;
import org.unitedinternet.cosmo.model.CollectionItem;
import org.unitedinternet.cosmo.model.CollectionLockedException;
import org.unitedinternet.cosmo.model.ContentItem;
import org.unitedinternet.cosmo.model.Item;
import org.unitedinternet.cosmo.util.CosmoQName;

public abstract class CalDavCollectionBase extends CalDavResourceBase
		implements CalDavCollection, ExtendedDavConstants {

	public static final CosmoQName RESOURCE_TYPE_COLLECTION = new CosmoQName(NAMESPACE.getURI(), XML_COLLECTION,
			NAMESPACE.getPrefix());

	private List<DavResource> members;

	protected Set<QName> getResourceTypes() {
		HashSet<QName> rt = new HashSet<QName>(1);
		rt.add(RESOURCE_TYPE_COLLECTION);
		return rt;
	}

	protected Set<String> getDeadPropertyFilter() {
		return DEAD_PROPERTY_FILTER;
	}

	@Override
	protected void updateItem() throws CosmoDavException {
		try {
			getContentService().updateCollection((CollectionItem) getItem());
		} catch (CollectionLockedException e) {
			throw new LockedException();
		}
	}

	@Override
	public boolean isCollection() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public DavResource findMember(String href) throws DavException {
        return memberToResource(href);
    }
	protected DavResource memberToResource(String uri) throws DavException {
		return davResourceFactory.createResource(locator, null);
	}

	protected DavResource memberToResource(Item item) throws DavException {
		String path;
		try {
			path = getResourcePath() + "/" + URLEncoder.encode(item.getName(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new CosmoDavException(e);
		}

		DavResourceLocator locator = davLocatorFactory.createResourceLocator(null, path);
		return davResourceFactory.createResource(locator, getSession());
	}

	public DavResourceIterator getCollectionMembers() {
		try {
			Set<CollectionItem> collectionItems = getContentService().findCollectionItems((CollectionItem) getItem());
			for (Item memberItem : collectionItems) {
				DavResource resource = memberToResource(memberItem);
				if (resource != null) {
					members.add(resource);
				}
			}
			return new DavResourceIteratorImpl(members);
		} catch (DavException e) {
			throw new CosmoException(e);
		}
	}

	protected void saveContent(DavItemContent member) throws CosmoDavException {
		CollectionItem collection = (CollectionItem) getItem();
		ContentItem content = (ContentItem) member.getItem();

		try {
			if (content.getCreationDate() != null) {

				content = getContentService().updateContent(content);
			} else {

				content = getContentService().createContent(collection, content);
			}
		} catch (CollectionLockedException e) {
			throw new LockedException();
		}

		member.setItem(content);
	}
	
	public MultiStatusResponse addCollection(DavCollection collection, DavPropertySet properties) throws CosmoDavException {
        if(!(collection instanceof DavCollectionBase)){
            throw new IllegalArgumentException("Expected instance of :[" + DavCollectionBase.class.getName() + "]");
        }
        
        DavCollectionBase base = (DavCollectionBase) collection;
        base.populateItem(null);
        MultiStatusResponse msr = base.populateAttributes(properties);
        if (!hasNonOK(msr)) {
            saveSubcollection(base);
            members.add(base);
        }
        return msr;
    }
	
	protected void saveSubcollection(DavItemCollection member)
            throws CosmoDavException {
        CollectionItem collection = (CollectionItem) getItem();
        CollectionItem subcollection = (CollectionItem) member.getItem();

        

        try {
            subcollection = getContentService().createCollection(collection,
                    subcollection);
            member.setItem(subcollection);
        } catch (CollectionLockedException e) {
            throw new LockedException();
        }
    }
	
	public void addContent(DavContent content, InputContext context) throws CosmoDavException {
        if(!(content instanceof DavContentBase)) {
            throw new IllegalArgumentException("Expected instance of : [" + DavContentBase.class.getName() + "]");
        }
        
        DavContentBase base = (DavContentBase) content;
        base.populateItem(context);
        saveContent(base);
        members.add(base);
    }
}
