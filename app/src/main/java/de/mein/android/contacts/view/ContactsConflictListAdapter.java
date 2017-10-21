package de.mein.android.contacts.view;

import android.app.Activity;
import android.content.Context;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mein.R;
import de.mein.android.contacts.data.db.ContactName;
import de.mein.android.contacts.service.AndroidContactsClientService;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactJoinDummy;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 10/18/17.
 */

public class ContactsConflictListAdapter extends BaseAdapter {
    private final LayoutInflater layoutInflator;
    private final Activity activity;
    private final Long localPhoneBookId;
    private final Long receivedPhoneBookId;
    private final AndroidContactsClientService service;
    private final ContactsDao contactsDao;
    private final PhoneBookDao phoneBookDao;
    private List<ContactJoinDummy> contactDummies = new ArrayList<ContactJoinDummy>();

    public ContactsConflictListAdapter(Activity activity, AndroidContactsClientService service, Long localPhoneBookId, Long receivedPhoneBookId) {
        this.activity = activity;
        this.layoutInflator = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.localPhoneBookId = localPhoneBookId;
        this.receivedPhoneBookId = receivedPhoneBookId;
        this.service = service;
        phoneBookDao = service.getDatabaseManager().getPhoneBookDao();
        contactsDao = service.getDatabaseManager().getContactsDao();
        init();
    }

    private void init() {
        try {
            contactsDao.contactsResource(localPhoneBookId);
            ISQLResource<ContactJoinDummy> resource = contactsDao.getDummiesForConflict(localPhoneBookId, receivedPhoneBookId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);

            N.readSqlResource(resource, joinDummy -> {
                contactDummies.add(joinDummy);
            });
            Set<Long> deletedLocalContactIds = new HashSet<>();
            Map<Long, Long> conflictingContactIds = new HashMap<>();
            Set<Long> newReceivedContactIds = new HashSet<>();
            ISQLResource<Contact> localResource = contactsDao.contactsResource(localPhoneBookId);
            Contact localContact = localResource.getNext();
            while (localContact != null) {
                List<ContactName> names = contactsDao.getWrappedAppendices(localContact.getId().v(), ContactName.class);
                if (names.size() == 1) {
                    ContactName contactName = names.get(0);
                    Contact receivedContact = contactsDao.getContactByName(receivedPhoneBookId, contactName.getName(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
                    if (receivedContact == null) {
                        deletedLocalContactIds.add(localContact.getId().v());
                    } else {
                        receivedContact.getChecked().v(true);
                        contactsDao.updateChecked(receivedContact);
                        if (receivedContact.getHash().notEqualsValue(localContact.getHash())) {
                            conflictingContactIds.put(localContact.getId().v(), receivedContact.getId().v());
                        }
                    }
                } else if (names.size() > 0) {
                    System.err.println("AndroidContactsClientService.checkConflict.TOO:MANY:NAMES");
                }
                localContact = localResource.getNext();
            }
            ISQLResource<Contact> receivedResource = contactsDao.contactsResource(receivedPhoneBookId, false);
            Contact receivedContact = receivedResource.getNext();
            while (receivedContact != null) {
                newReceivedContactIds.add(receivedContact.getId().v());
                receivedContact = receivedResource.getNext();
            }

        } catch (SqlQueriesException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getCount() {
        return contactDummies.size();
    }

    @Override
    public Object getItem(int position) {
        return contactDummies.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ContactJoinDummy dummy = contactDummies.get(position);
        View view = null;
        if (dummy.both()) {
            view = layoutInflator.inflate(R.layout.listitem_contacts_conflict_double, null);
        } else {
            view = layoutInflator.inflate(R.layout.listitem_contacts_conflict_left, null);
        }
        TextView txtName = view.findViewById(R.id.txtName);
        txtName.setText(dummy.getName().v());
        ImageView imageView = view.findViewById(R.id.image);
        return view;
    }
}
