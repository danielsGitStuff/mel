package de.mein.drive.sql;

import de.mein.core.serialize.JsonIgnore;
import de.mein.sql.Pair;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;


public class FsDirectory extends FsEntry {

    protected FsDirectory parent;
    protected List<FsFile> files = new ArrayList<>();
    protected List<FsDirectory> subDirectories = new ArrayList<>();

    public FsDirectory() {

    }

    public boolean isRoot() {
        return parentId.v() == null;
    }


    @Override
    protected void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files) {
        contentHash.v(calcCHash(subDirectories, files));
    }

    public static String calcCHash(List<FsDirectory> subDirectories, List<FsFile> files) {
        Integer hash = 0;
        for (FsFile file : files) {
            hash += file.getName().calcHash();
        }
        for (FsDirectory subDirectory : subDirectories) {
            hash += subDirectory.getName().calcHash(); //+ subDirectory.getVersion().calcHash();
        }
        return hash.toString();
    }

    @Override
    public void calcContentHash() {
        calcContentHash(subDirectories, files);
    }

    @JsonIgnore
    protected File original;

    public File getOriginal() {
        return original;
    }

    public FsDirectory setOriginalFile(File original) {
        this.original = original;
        return this;
    }


    public List<FsFile> getFiles() {
        return files;
    }

    public List<FsDirectory> getSubDirectories() {
        return subDirectories;
    }

    public FsDirectory(java.io.File dir) {
        if (!dir.isFile()) {
            name.v(dir.getName());
            original = dir;
        } else {
            System.err.println("Directory.Directory() ... got a non Directory");
        }
        init();
    }

    public FsDirectory(FsDirectory srcDir) {
        this.original = srcDir.getOriginal();
        this.getName().v(srcDir.getName());
        init();
    }

    public void setFiles(List<FsFile> files) {
        this.files = files;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void init() {
        super.init();
        isDirectory.v(true);
    }

    public File[] listFiles() {
        if (original == null) {
            // ...
        }
        return original.listFiles();
    }

    public File[] listFiles(FileFilter directoryFileFilter) {
        if (original == null) {
            // ...
        }
        return original.listFiles(directoryFileFilter);
    }


    public Pair<String> getName() {
        return name;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<Long> getParentId() {
        return parentId;
    }

    public Pair<Long> getVersion() {
        return version;
    }

    public FsDirectory addFile(FsFile f) {
        files.add(f);
        return this;
    }

    public FsDirectory addSubDirectory(FsDirectory subDir) {
        subDirectories.add(subDir);
        return this;
    }


    public FsDirectory setParent(FsDirectory parent) {
        this.parent = parent;
        return this;
    }

    public FsDirectory getParent() {
        return parent;
    }


    public void setSubDirectories(List<FsDirectory> subDirectories) {
        this.subDirectories = subDirectories;
    }

    public FsDirectory setName(String name) {
        this.name.v(name);
        return this;
    }

    public FsDirectory setParentId(Long parentId) {
        this.parentId.v(parentId);
        return this;
    }

    public FsDirectory setId(Long id) {
        this.id.v(parentId);
        return this;
    }

    @Override
    public String toString() {
        return (name.v() == null) ? super.toString() : "dir: " + name.v();
    }

    public FsEntry setVersion(long version) {
        this.version.v(version);
        return this;
    }
}

