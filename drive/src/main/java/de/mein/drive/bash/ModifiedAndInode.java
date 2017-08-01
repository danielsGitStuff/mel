package de.mein.drive.bash;

/**
 * Created by xor on 8/1/17.
 */
class ModifiedAndInode {
    private final Long modified;
    private final Long iNode;


    ModifiedAndInode(Long modified, Long iNode) {
        this.modified = modified;
        this.iNode = iNode;
    }

    public Long getiNode() {
        return iNode;
    }

    public Long getModified() {
        return modified;
    }
}
