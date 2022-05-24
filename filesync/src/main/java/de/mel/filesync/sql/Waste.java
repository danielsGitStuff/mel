package de.mel.filesync.sql;

import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

import java.sql.Date;

/**
 * Created by xor on 1/27/17.
 */
public class Waste extends SQLTableObject {
    private static final String HASH = "hash";
    private static final String DELETED = "deleted";
    private static final String SIZE = "size";
    private static final String NAME = "name";
    private static final String INPLACE = "inplace";
    private static final String INODE = "inode";
    private static final String MODIFIED = "modified";
    private static final String ID = "id";
    private static final String FLAG_DELETE = "f_delete";
    private Pair<String> hash = new Pair<>(String.class, HASH);
    private Pair<String> name = new Pair<>(String.class, NAME);
    private Pair<String> deleted = new Pair<>(String.class, DELETED);
    private Pair<Long> size = new Pair<>(Long.class, SIZE);
    private Pair<Long> inode = new Pair<>(Long.class, INODE);
    private Pair<Long> modified = new Pair<>(Long.class, MODIFIED);
    private Pair<Boolean> inplace = new Pair<>(Boolean.class, INPLACE);
    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<Boolean> flagDelete = new Pair<>(Boolean.class, FLAG_DELETE);

    public Waste() {
        init();
    }

    @Override
    public String getTableName() {
        return "waste";
    }

    @Override
    protected void init() {
        populateInsert(hash, deleted, size, name, inplace, inode, modified, flagDelete);
        populateAll(id);
    }

    public Pair<Boolean> getFlagDelete() {
        return flagDelete;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getDeleted() {
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

    public static Waste fromFsFile(FsFile file) {
        Waste waste = new Waste();
        waste.getHash().v(file.getContentHash());
        waste.getInode().v(file.getiNode());
        waste.getInplace().v(false);
        waste.getName().v(file.getName());
        waste.getSize().v(file.getSize());
        waste.getModified().v(file.getModified());
        waste.getFlagDelete().v(false);
        return waste;
    }
}
