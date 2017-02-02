package de.mein.drive.sql;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

import java.sql.Date;

/**
 * Created by xor on 1/27/17.
 */
public class Waste extends SQLTableObject {
    private static String HASH = "hash";
    private static String DELETED = "deleted";
    private static String SIZE = "size";
    private static String NAME = "name";
    private static String INPLACE = "inplace";
    private static String INODE = "inode";
    private static String MODIFIED = "modified";
    private Pair<String> hash = new Pair<>(String.class, HASH);
    private Pair<String> name = new Pair<>(String.class, NAME);
    private Pair<Date> deleted = new Pair<>(Date.class, DELETED);
    private Pair<Long> size = new Pair<>(Long.class, SIZE);
    private Pair<Long> inode = new Pair<>(Long.class, INODE);
    private Pair<Long> modified = new Pair<>(Long.class, MODIFIED);
    private Pair<Boolean> inplace = new Pair<>(Boolean.class, INPLACE);

    public Waste() {
        init();
    }

    @Override
    public String getTableName() {
        return "waste";
    }

    @Override
    protected void init() {
        populateInsert(hash, deleted, size, name, inplace, inode, modified);
        populateAll();
    }

    public Pair<Date> getDeleted() {
        return deleted;
    }

    public Pair<Long> getSize() {
        return size;
    }

    public Pair<String> getHash() {
        return hash;
    }

    public Pair<String> getName() {
        return name;
    }

    public Pair<Boolean> getInplace() {
        return inplace;
    }

    public Pair<Long> getInode() {
        return inode;
    }

    public Pair<Long> getModified() {
        return modified;
    }
}
