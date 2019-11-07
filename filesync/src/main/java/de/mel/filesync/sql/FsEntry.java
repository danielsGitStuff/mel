package de.mel.filesync.sql;

import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

import java.util.List;

/**
 * Created by xor on 29.08.2016.
 */
public abstract class FsEntry extends SQLTableObject implements SerializableEntity {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String PARENT_ID = "parentid";
    private static final String VERSION = "version";
    private static final String CONTENT_HASH = "contenthash";
    private static final String DIR = "dir";
    private static final String INODE = "inode";
    private static final String SYNCED = "synced";
    private static final String MODIFIED = "modified";
    private static final String CREATED = "created";
    private static final String SIZE = "size";
    private static final String SYMLINK = "sym";

    protected Pair<Long> id = new Pair<>(Long.class, ID);
    protected Pair<String> name = new Pair<>(String.class, NAME);
    protected Pair<Long> parentId = new Pair<>(Long.class, PARENT_ID);
    protected Pair<Long> version = new Pair<>(Long.class, VERSION);
    protected Pair<String> contentHash = new Pair<>(String.class, CONTENT_HASH, "0");
    protected Pair<Boolean> isDirectory = new Pair<>(Boolean.class, DIR, false);
    @JsonIgnore
    protected Pair<Long> iNode = new Pair<>(Long.class, INODE);
    @JsonIgnore
    protected Pair<Long> modified = new Pair<>(Long.class, MODIFIED);
    protected Pair<Long> created = new Pair<>(Long.class, CREATED);

    // tells whether or not the file was already put/seen in its logical place on FS
    protected Pair<Boolean> synced = new Pair<>(Boolean.class, SYNCED);
    protected Pair<Long> size = new Pair<>(Long.class, SIZE);
    protected Pair<String> symLink = new Pair<>(String.class, SYMLINK);

    public FsEntry() {
        init();
    }

    public FsWriteEntry toFsWriteEntry() {
        return new FsWriteEntry(this);
    }

//    protected abstract void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files);
//
//    public abstract void calcContentHash();


    public String getTableName() {
        return "fsentry";
    }

    public Pair<Boolean> getSynced() {
        return synced;
    }

    @Override
    protected void init() {
        populateInsert(name, parentId, version, contentHash, isDirectory, synced, iNode, modified, created, size, symLink);
        populateAll(id);
    }

    public Pair<Long> getVersion() {
        return version;
    }

    public Pair<Boolean> getIsDirectory() {
        return isDirectory;
    }

    public Pair<Long> getParentId() {
        return parentId;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getContentHash() {
        return contentHash;
    }

    public Pair<String> getName() {
        return name;
    }

    public Pair<Long> getiNode() {
        return iNode;
    }

    public Pair<Long> getModified() {
        return modified;
    }

    public FsEntry copyInstance() {
        FsEntry ins;
        if (isDirectory.v()) {
            ins = new FsDirectory();
        } else {
            ins = new FsFile();
        }
        for (int i = 0; i < allAttributes.size(); i++) {
            ins.getAllAttributes().get(i).v((Pair) allAttributes.get(i));
        }
        return ins;
    }

    public Pair<Long> getSize() {
        return size;
    }

    public void setSymLink(String symLink) {
        this.symLink.v(symLink);
    }

    public Pair<String> getSymLink() {
        return symLink;
    }

    public boolean isSymlink() {
        return symLink.notNull();
    }

    public Pair<Long> getCreated() {
        return created;
    }

    public FsEntry setModified(Long modified) {
        this.modified.v(modified);
        return this;
    }

    public FsEntry setCreated(Long created) {
        this.created.v(created);
        return this;
    }

    public FsEntry setInode(Long inode) {
        this.iNode.v(inode);
        return this;
    }
}
