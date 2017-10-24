package de.mein.android.contacts.view;

import android.app.Activity;
import android.content.Context;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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
import de.mein.contacts.data.db.ContactAppendix;
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
        RelativeLayout view = null;
        if (dummy.both()) {
            view = (RelativeLayout) layoutInflator.inflate(R.layout.listitem_contacts_conflict_double, null);
            try {
                Integer lastIdLeft = R.id.txtName;
                Integer lastIdRight = R.id.txtName;
                List<ContactAppendix> appendices = contactsDao.getAppendicesExceptName(dummy.getLeftId().v(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                for (ContactAppendix appendix : appendices) {
                    TextView leftText = new TextView(activity);
                    leftText.setId(View.generateViewId());
                    if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        leftText.setText("phone: " + appendix.getColumnValue(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    }
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.BELOW, lastIdLeft);
                    params.addRule(RelativeLayout.CENTER_VERTICAL);
                    params.addRule(RelativeLayout.END_OF, R.id.imageLeft);
                    lastIdLeft = leftText.getId();
                    view.addView(leftText, params);
                }
                // right side
                appendices = contactsDao.getAppendicesExceptName(dummy.getRightId().v(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                for (ContactAppendix appendix : appendices) {
                    TextView righText = new TextView(activity);
                    righText.setId(View.generateViewId());
                    if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        righText.setText("phone: " + appendix.getColumnValue(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    }
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.BELOW, lastIdRight);
                    params.addRule(RelativeLayout.CENTER_VERTICAL);
                    params.addRule(RelativeLayout.END_OF, R.id.strut);
                    params.addRule(RelativeLayout.START_OF,R.id.imageRight);
                    lastIdRight = righText.getId();
                    view.addView(righText, params);
                }
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        } else {
            view = (RelativeLayout) layoutInflator.inflate(R.layout.listitem_contacts_conflict_left, null);
        }
        TextView txtName = view.findViewById(R.id.txtName);
        txtName.setText(dummy.getName().v());
        ImageView imageView = view.findViewById(R.id.image);
        return view;
    }
}