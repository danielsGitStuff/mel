package de.mel.contacts.data.db;

import java.util.ArrayList;
import java.util.List;

import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.MD5er;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

/**
 * Created by xor on 9/22/17.
 */

public class Contact extends SQLTableObject implements SerializableEntity {

    public static final String ID = "id";
    public static final String PID = "pid";
    public static final String IMAGE = "image";
    public static final String AID = "aid";
    public static final String HASH = "deephash";
    public static final String FLAG_CHECKED = "checked";
    private static final String MIME_PHOTO = "vnd.android.cursor.item/photo";
    @JsonIgnore
    private Pair<Long> id = new Pair<>(Long.class, ID);
    @JsonIgnore
    private Pair<Long> phonebookId = new Pair<>(Long.class, PID);
    @JsonIgnore
    private Pair<Boolean> checked = new Pair<>(Boolean.class, FLAG_CHECKED, false);

    private Pair<byte[]> image = new Pair<>(byte[].class, IMAGE);

    private List<ContactAppendix> appendices = new ArrayList<>();

    @JsonIgnore
    private Pair<Long> androidId = new Pair<>(Long.class, AID);
    private Pair<String> hash = new Pair<>(String.class, HASH);

    public Contact() {
        init();
    }


    public Pair<Long> getId() {
        return id;
    }


    public Pair<Long> getPhonebookId() {
        return phonebookId;
    }

    public Pair<byte[]> getImage() {
        return image;
    }

    @Override
    public String getTableName() {
        return "contacts";
    }

    @Override
    protected void init() {
        populateInsert(phonebookId, image, checked);
        insertAttributes.add(hash);
        populateAll(id);
    }


    public Pair<Long> getAndroidId() {
        return androidId;
    }

    public Pair<String> getHash() {
        return hash;
    }

    /**
     * hashes but does not digest md5er
     *
     * @param md5er
     */
    public void hash(MD5er md5er) {
        hash();
        Pair.hash(md5er, hash);
    }

    public String hash() {
        MD5er md5er = new MD5er();
        for (ContactAppendix appendix : appendices) {
            if (appendix.getMimeType().notEqualsValue(MIME_PHOTO))
                appendix.hash(md5er);
        }
        md5er.hash(image.v());
        hash.v(md5er.digest());
        return hash.v();
    }

    public void setAppendices(List<ContactAppendix> appendices) {
        this.appendices = appendices;
    }

    public List<ContactAppendix> getAppendices() {
        return appendices;
    }

    public Contact addAppendix(ContactAppendix appendix) {
        appendices.add(appendix);
        return this;
    }

    public Pair<Boolean> getChecked() {
        return checked;
    }
}
