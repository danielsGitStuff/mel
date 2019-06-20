package de.mein.drive.bash;

/**
 * Created by xor on 8/1/17.
 */
public class FsBashDetails {
    private final Long modified;
    private final Long iNode;
    private final boolean isSymLink;


    public FsBashDetails(Long modified, Long iNode, boolean isSymLink) {
        this.modified = modified;
        this.iNode = iNode;
        this.isSymLink = isSymLink;
    }

    public Long getiNode() {
        return iNode;
    }

    public Long getModified() {
        return modified;
    }

    public boolean isSymLink() {
        return isSymLink;
    }
}
