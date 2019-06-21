package de.mein.drive.bash;

/**
 * Created by xor on 8/1/17.
 */
public class FsBashDetails {
    private final Long modified;
    private final Long iNode;
    private final boolean isSymLink;
    private final String symLinkTarget;
    private final String name;


    public FsBashDetails(Long modified, Long iNode, boolean isSymLink, String symLinkTarget, String name) {
        this.modified = modified;
        this.iNode = iNode;
        this.isSymLink = isSymLink;
        this.symLinkTarget = symLinkTarget;
        this.name = name;
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

    public String getSymLinkTarget() {
        return symLinkTarget;
    }

    public String getName() {
        return name;
    }
}
