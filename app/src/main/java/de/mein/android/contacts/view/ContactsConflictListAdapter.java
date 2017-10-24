package de.mein.android.contacts.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import de.mein.sql.Pair;
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
    private List<ContactJoinDummy> contactDummies = new ArrayList<>();
    private final int red = Color.argb(120, 125, 0, 0);
    private final int green = Color.argb(120, 0, 120, 0);
    private final int normal = Color.argb(255, 100, 100, 100);

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

    public List<ContactJoinDummy> getContactDummies() {
        return contactDummies;
    }

    private void init() {
        try {
            contactsDao.contactsResource(localPhoneBookId);
            ISQLResource<ContactJoinDummy> resource = contactsDao.getDummiesForConflict(localPhoneBookId, receivedPhoneBookId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
            N.readSqlResource(resource, dummy -> {
                contactDummies.add(dummy);
                if (dummy.both())
                    dummy.setChoice(dummy.getRightId().v());
                else if (dummy.getRightId().isNull())
                    dummy.setChoice(dummy.getLeftId().v());
                else if (dummy.getRightId().notNull())
                    dummy.setChoice(dummy.getRightId().v());
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

    private TextView createTextView() {
        TextView textView = new TextView(activity);
        textView.setId(View.generateViewId());
        return textView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ContactJoinDummy dummy = contactDummies.get(position);
        RelativeLayout view = null;
        if (dummy.both()) {
            view = (RelativeLayout) layoutInflator.inflate(R.layout.listitem_contacts_conflict_double, null);
            RadioButton rbLeft = view.findViewById(R.id.rbLeft);
            RadioButton rbRight = view.findViewById(R.id.rbRight);
            ImageView imageLeft = view.findViewById(R.id.imageLeft);
            ImageView imageRight = view.findViewById(R.id.imageRight);
            view.setOnClickListener(v -> {
                //unselect if clicked
                if (dummy.getChoice() != null) {
                    dummy.setChoice(null);
                    rbLeft.setChecked(false);
                    rbRight.setChecked(false);
                }
                adjustSingle(v, dummy.getChoice() != null);
            });

            rbLeft.setOnClickListener(v -> {
                if (rbLeft.isChecked()) {
                    dummy.setChoice(dummy.getLeftId().v());
                    rbRight.setChecked(false);
                }
                adjustSingle(v, dummy.getChoice() != null);

            });
            rbRight.setOnClickListener(v -> {
                if (rbRight.isChecked()) {
                    dummy.setChoice(dummy.getRightId().v());
                    rbLeft.setChecked(false);
                }
                adjustSingle(v, dummy.getChoice() != null);
            });
            // adjust radio button
            rbLeft.setChecked(false);
            rbRight.setChecked(false);
            if (dummy.getChoice() != null) {
                if (dummy.getRightId().equalsValue(dummy.getChoice())) {
                    rbRight.setChecked(true);
                } else if (dummy.getLeftId().equalsValue(dummy.getChoice())) {
                    rbLeft.setChecked(true);
                }
            }
            try {
                Integer lastIdLeft = R.id.txtName;
                Integer lastIdRight = R.id.txtName;
                int leftCount = 0;
                int rightCount = 0;
                List<ContactAppendix> appendices = contactsDao.getAppendicesExceptName(dummy.getLeftId().v(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                for (ContactAppendix appendix : appendices) {
                    TextView leftText = null;
                    if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        leftText = createTextView();
                        leftText.setText("phone: " + appendix.getColumnValue(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    } else if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
                        leftText = createTextView();
                        leftText.setText("EMail: " + appendix.getColumnValue(ContactsContract.CommonDataKinds.Email.ADDRESS));
                    } else if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)) {
                        byte[] bytes = appendix.getBlob().v();
                        InputStream inputStream = new ByteArrayInputStream(bytes);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        imageLeft.setImageBitmap(bitmap);
                        System.out.println("ContactsConflictListAdapter.getView");
                    }
                    if (leftText != null) {
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        params.addRule(RelativeLayout.BELOW, lastIdLeft);
                        if (leftCount < 1) {
                            params.addRule(RelativeLayout.CENTER_VERTICAL);
                            params.addRule(RelativeLayout.END_OF, R.id.imageLeft);
                        }else {
                            params.addRule(RelativeLayout.END_OF, R.id.rbLeft);
                        }
                        params.addRule(RelativeLayout.ALIGN_END, R.id.strut);
                        lastIdLeft = leftText.getId();
                        view.addView(leftText, params);
                        leftCount++;
                    }
                }
                // right side
                appendices = contactsDao.getAppendicesExceptName(dummy.getRightId().v(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                for (ContactAppendix appendix : appendices) {
                    TextView rightText = null;
                    if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        rightText = createTextView();
                        rightText.setText("phone: " + appendix.getColumnValue(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    } else if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
                        rightText = createTextView();
                        rightText.setText("EMail: " + appendix.getColumnValue(ContactsContract.CommonDataKinds.Email.ADDRESS));
                    } else if (appendix.getMimeType().equalsValue(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)) {
                        byte[] bytes = appendix.getBlob().v();
                        InputStream inputStream = new ByteArrayInputStream(bytes);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        imageRight.setImageBitmap(bitmap);
                        System.out.println("ContactsConflictListAdapter.getView");
                    }
                    if (rightText != null) {
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        params.addRule(RelativeLayout.BELOW, lastIdRight);
                        if (rightCount < 1) {
                            params.addRule(RelativeLayout.CENTER_VERTICAL);
                            params.addRule(RelativeLayout.START_OF, R.id.imageRight);
                        }else {
                            params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        }
                        params.addRule(RelativeLayout.END_OF,R.id.strut);
//                        params.addRule(RelativeLayout.ALIGN_END, R.id.strut);
                        lastIdRight = rightText.getId();
                        view.addView(rightText, params);
                        rightCount++;
                    }
                }
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        } else {
            view = (RelativeLayout) layoutInflator.inflate(R.layout.listitem_contacts_conflict_left, null);
            RelativeLayout finalView = view;
            view.setOnClickListener(v -> {
                if (dummy.getChoice() != null) {
                    dummy.setChoice(null);
                    adjustSingle(finalView, false);
                } else {
                    dummy.setChoice(dummy.getLeftId().notNull() ? dummy.getLeftId().v() : dummy.getRightId().v());
                    adjustSingle(finalView, true);
                }
            });
        }
        adjustSingle(view, dummy.getChoice() != null);
        TextView txtName = view.findViewById(R.id.txtName);
        txtName.setText(dummy.getName().v());
        ImageView imageView = view.findViewById(R.id.image);
        return view;
    }

    private void adjustSingle(View view, boolean selected) {
        if (selected) {
            view.setBackgroundColor(green);
        } else {
            view.setBackgroundColor(normal);
        }
    }
}
