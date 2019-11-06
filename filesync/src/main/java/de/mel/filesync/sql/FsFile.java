package de.mel.filesync.sql;

import de.mel.auth.file.AbstractFile;
import de.mel.core.serialize.JsonIgnore;

import java.util.List;

public class FsFile extends FsEntry {


    @JsonIgnore
    protected AbstractFile original;

    protected FsDirectory directory;

    public FsFile() {
    }

    @Override
    protected void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files) {

    }

    @Override
    public void calcContentHash() {

    }

    public FsFile(AbstractFile f) {
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

    public AbstractFile getOriginal() {
        return original;
    }


    public FsFile setName(String name) {
        this.name.v(name);
        return this;
    }
}
