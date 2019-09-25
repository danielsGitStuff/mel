package de.mel.drive.sql;

import de.mel.auth.file.AFile;
import de.mel.core.serialize.JsonIgnore;

import java.io.File;
import java.util.List;

public class FsFile extends FsEntry {


    @JsonIgnore
    protected AFile original;

    protected FsDirectory directory;

    public FsFile() {
    }

    @Override
    protected void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files) {

    }

    @Override
    public void calcContentHash() {

    }

    public FsFile(AFile f) {
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

    public AFile getOriginal() {
        return original;
    }


    public FsFile setName(String name) {
        this.name.v(name);
        return this;
    }
}
