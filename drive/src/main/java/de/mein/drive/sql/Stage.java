package de.mein.drive.sql;

import de.mein.Lok;
import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 11/20/16.
 */
public class Stage extends SQLTableObject implements SerializableEntity {
    private static final String ID = "id";
    private static final String PARENTID = "parentid";
    private static final String FSID = "fsid";
    private static final String FSPARENTID = "fsparentid";
    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final String CONTENTHASH = "contenthash";
    private static final String DIR = "dir";
    private static final String INODE = "inode";
    private static final String MODIFIED = "modified";
    private static final String CREATED = "created";
    private static final String DELETED = "deleted";
    private static final String STAGESET = "stageset";
    private static final String SIZE = "size";
    private static final String SYNCED = "synced";
    private static final String MERGED = "merged";
    private static final String ORDER = "ord";
    private static final String SYMLINK = "sym";
    private static final String REL_PATH = "path";
    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<Long> parentId = new Pair<>(Long.class, PARENTID);
    private Pair<Long> fsId = new Pair<>(Long.class, FSID);
    private Pair<Long> fsParentId = new Pair<>(Long.class, FSPARENTID);
    private Pair<String> name = new Pair<>(String.class, NAME);
    private Pair<Long> version = new Pair<>(Long.class, VERSION);
    private Pair<String> contentHash = new Pair<>(String.class, CONTENTHASH);
    private Pair<Boolean> isDirectory = new Pair<>(Boolean.class, DIR);
    @JsonIgnore
    private Pair<Long> iNode = new Pair<>(Long.class, INODE);
    @JsonIgnore
    private Pair<Long> modified = new Pair<>(Long.class, MODIFIED);
    private Pair<Long> created = new Pair<>(Long.class, CREATED);
    private Pair<Boolean> deleted = new Pair<>(Boolean.class, DELETED).setSetListener(value -> {
        if (value)
            Lok.debug(); // todo debug
        return value;
    });
    @JsonIgnore
    private Pair<Long> stageSet = new Pair<>(Long.class, STAGESET);
    private Pair<Long> size = new Pair<Long>(Long.class, SIZE);
    @JsonIgnore
    private Pair<Boolean> synced = new Pair<>(Boolean.class, SYNCED);
    @JsonIgnore
    private Pair<Boolean> merged = new Pair<>(Boolean.class, MERGED, false);
    private Pair<Long> order = new Pair<>(Long.class, ORDER);

    private Pair<String> symLink = new Pair<>(String.class, SYMLINK);
//    @JsonIgnore
//    private Pair<String> relativePath = new Pair<>(String.class, REL_PATH);


    public Stage() {
        init();
    }

    public Stage(long stageSetId, Stage src) {
        init();
        for (int i = 0; i < src.insertAttributes.size(); i++) {
            Pair srcPair = src.insertAttributes.get(i);
            Pair pair = insertAttributes.get(i);
            pair.v(srcPair);
        }
        stageSet.v(stageSetId);
        order.nul();
    }

    @Override
    public String getTableName() {
        return "stage";
    }

    @Override
    protected void init() {
        populateInsert(parentId, fsId, fsParentId, name, version, contentHash, isDirectory, symLink, iNode, modified, created, deleted, stageSet, size, synced, merged, order);
        populateAll(id);
    }

    public Long getId() {
        return id.v();
    }

    public Stage setId(Long id) {
        this.id.v(id);
        return this;
    }

    public Pair<Long> getCreatedPair() {
        return created;
    }

    public Long getCreated() {
        return created.v();
    }

    public Stage setCreated(Long created) {
        this.created.v(created);
        return this;
    }

    public Boolean isMerged() {
        return merged.v();
    }

    public Stage setMerged(boolean merged) {
        this.merged.v(merged);
        return this;
    }

    public Pair<Boolean> getMergedPair() {
        return merged;
    }

    public Long getParentId() {
        return parentId.v();
    }

    public Stage setParentId(Long parentId) {
        this.parentId.v(parentId);
        return this;
    }

    public Pair<Long> getOrderPair() {
        return order;
    }

    public Long getOrder() {
        return order.v();
    }

    public Stage setOrder(Long order) {
        this.order.v(order);
        return this;
    }

    public Long getFsId() {
        return fsId.v();
    }

    public Stage setFsId(Long fsId) {
        this.fsId.v(fsId);
        return this;
    }

    public Long getFsParentId() {
        return fsParentId.v();
    }

    public Stage setFsParentId(Long fsParentId) {
        this.fsParentId.v(fsParentId);
        return this;
    }

    public String getName() {
        return name.v();
    }

    public Stage setName(String name) {
        this.name.v(name);
        return this;
    }

    public Long getVersion() {
        return version.v();
    }

    public Stage setVersion(Long version) {
        this.version.v(version);
        return this;
    }

    public String getContentHash() {
        return contentHash.v();
    }

    public Stage setContentHash(String contentHash) {
        this.contentHash.v(contentHash);
        return this;
    }

    public Boolean getIsDirectory() {
        return isDirectory.v();
    }

    public Stage setIsDirectory(Boolean isDirectory) {
        this.isDirectory.v(isDirectory);
        return this;
    }

    public Long getiNode() {
        return iNode.v();
    }

    public Stage setiNode(Long iNode) {
        this.iNode.v(iNode);
        return this;
    }

    public Long getModified() {
        return modified.v();
    }

    public Stage setModified(Long modified) {
        this.modified.v(modified);
        return this;
    }

    public Boolean getDeleted() {
        return deleted.v();
    }

    public Pair<Boolean> getDeletedPair() {
        return deleted;
    }

    public Stage setDeleted(Boolean deleted) {
        this.deleted.v(deleted);
        return this;
    }

    public Stage setStageSet(Long stageSet) {
        this.stageSet.v(stageSet);
        return this;
    }

    public Pair<String> getSymLinkPair() {
        return symLink;
    }

    public String getSymLink() {
        return symLink.v();
    }

    public Boolean isSymLink() {
        return symLink.notNull();
    }


    public Long getStageSet() {
        return stageSet.v();
    }

    public Pair<Long> getFsIdPair() {
        return fsId;
    }

    public Pair<Long> getFsParentIdPair() {
        return fsParentId;
    }

    public Pair<String> getNamePair() {
        return name;
    }

    public Pair<Long> getStageSetPair() {
        return stageSet;
    }

    public Pair<Long> getIdPair() {
        return id;
    }

    public Pair<Long> getParentIdPair() {
        return parentId;
    }

    public Pair<Boolean> getIsDirectoryPair() {
        return isDirectory;
    }

    @Override
    public String toString() {
        return "n: " + name.v() + " id:" + id.v() + " set: " + stageSet.v() + " d: " + isDirectory.v() + " s: " + synced.v();
    }

    public Stage setSize(Long size) {
        this.size.v(size);
        return this;
    }

    public Pair<Long> getSizePair() {
        return size;
    }

    public Long getSize() {
        return size.v();
    }

    public Pair<Boolean> getSyncedPair() {
        return synced;
    }

    public Boolean getSynced() {
        return synced.v();
    }

    public Stage setSynced(Boolean synced) {
        this.synced.v(synced);
        return this;
    }

    public Stage mergeValuesFrom(Stage source) {
        version.v(source.getVersion());
        name.v(source.getName());
        modified.v(source.getModified());
        iNode.v(source.getiNode());
        contentHash.v(source.getContentHash());
        isDirectory.v(source.getIsDirectory());
        size.v(source.getSize());
        deleted.v(source.getDeleted());
        synced.v(source.getSynced());
        fsId.v(source.getFsId());
        fsParentId.v(source.getFsParentId());
        return this;
    }

    public Pair<Long> getiNodePair() {
        return iNode;
    }

    public Pair<Long> getModifiedPair() {
        return modified;
    }

    public Pair<String> getContentHashPair() {
        return contentHash;
    }

    public Stage setSymLink(String symLink) {
        this.symLink.v(symLink);
        return this;
    }
}
