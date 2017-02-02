package de.mein.drive.sql;

import de.mein.core.serialize.JsonIgnore;

import java.io.File;
import java.util.List;

public class FsFile extends FsEntry  {



    @JsonIgnore
    protected File original;

    protected FsDirectory directory;

    public FsFile() {
    }

    @Override
    protected void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files) {

    }

    @Override
    public void calcContentHash() {

    }

    public FsFile(java.io.File f) {
        name.v(f.getName());
        original = f;
        init();
    }

    public FsFile(FsFile source) {
        super();
        init();
        id.v(source.id);
        version.v(source.version);
        parentId.v(source.parentId);
        name.v(source.name);
        contentHash.v(source.contentHash);
        synced.v(false);
    }

    public void setDirectory(FsDirectory directory) {
        this.directory = directory;
    }

    public FsDirectory getDirectory() {
        return directory;
    }

    public File getOriginal() {
        return original;
    }


}
