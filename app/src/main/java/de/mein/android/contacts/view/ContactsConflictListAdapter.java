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
    private List<Long> contactIdList = new ArrayList<>();

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
            ISQLResource<ContactJoinDummy> resource = contactsDao.getDummiesForConflict(localPhoneBookId,receivedPhoneBookId,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);

            Set<Long> deletedLocalContactIds = new HashSet<>();
            Map<Long, Long> conflictingContactIds = new HashMap<>();
            Set<Long> newReceivedContactIds = new HashSet<>();
            ISQLResource<Contact> localResource = contactsDao.contactsResource(localPhoneBookId);
            Contact localContact = localResource.getNext();
            while (localContact != null) {
                List<ContactName> names = contactsDao.getWrappedAppendices(localContact.getId().v(), ContactName.class);
                if (names.size() == 1) {
                    ContactName contactName = names.get(0);
                    Contact receivedContact = contactsDao.getContactByName(contactName.getName(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
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
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = layoutInflator.inflate(R.layout.listitem_drive_conflict, null);
        TextView txtName = view.findViewById(R.id.txtName);
        ImageView imageView = view.findViewById(R.id.image);
        return view;
    }
}
