package de.mein.drive.sql;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.core.serialize.JsonIgnore;
import de.mein.sql.Hash;
import de.mein.sql.Pair;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


public class FsDirectory extends FsEntry {

    protected FsDirectory parent;
    protected List<FsFile> files = new ArrayList<>();
    @JsonIgnore
    protected Set<String> contentSet = new HashSet<>();
    protected List<FsDirectory> subDirectories = new ArrayList<>();

    public FsDirectory() {
        synced.v(true);
    }

    public boolean isRoot() {
        return parentId.v() == null;
    }

    private static void feedToMessageDigest(MessageDigest digest, List<? extends FsEntry> entries, String appendix) {
        List<String> sorted = new ArrayList<>(entries.size());
        N.forEach(entries, fsDirectory -> sorted.add(fsDirectory.getName().v()));
        Collections.sort(sorted);
        N.forEach(sorted, name -> digest.update(name.getBytes()));
        digest.update(appendix.getBytes());
    }

    private static String calcDirectoryContentHash(List<FsDirectory> dirs, List<FsFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            //since there is no guarantee that hashCode() delivers the same hashes on different machines the order inside of a Collection/Set
            //may vary. to work around this we sort the list before feeding the hash algorithm.
            feedToMessageDigest(digest, dirs, "d");
            feedToMessageDigest(digest, files, "f");
            return Hash.bytesToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "exception :(";
    }


    @Override
    protected void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files) {
        contentHash.v(calcDirectoryContentHash(subDirectories, files));
    }

    private static String calcCHash(List<FsDirectory> subDirectories, List<FsFile> files) {
        Integer hash = 0;
        for (FsFile file : files) {
            hash += file.getName().calcHash();
        }
        for (FsDirectory subDirectory : subDirectories) {
            hash += subDirectory.getName().calcHash(); //+ subDirectory.getOldVersion().calcHash();
        }
        return hash.toString();
    }

    @Override
    public void calcContentHash() {
        calcContentHash(subDirectories, files);
    }

    @JsonIgnore
    protected AFile original;

    public AFile getOriginal() {
        return original;
    }

    public FsDirectory setOriginalFile(AFile original) {
        this.original = original;
        return this;
    }


    public List<FsFile> getFiles() {
        return files;
    }

    public List<FsDirectory> getSubDirectories() {
        return subDirectories;
    }

    public FsDirectory(AFile dir) {
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

    public AFile[] listFiles() {
        if (original == null) {
            // ...
        }
        return original.listFiles();
    }


    public AFile[] listDirectories() {
        if (original == null) {
            // ...
        }
        return original.listDirectories();
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
        if (!contentSet.contains(f.getName().v())) {
            files.add(f);
            contentSet.add(f.getName().v());
            //todo debug
            if (f.getName().isNull())
                Lok.debug("FsDirectory.addFile");
        }
        return this;
    }

    public FsDirectory addSubDirectory(FsDirectory subDir) {
        if (!contentSet.contains(subDir.getName().v())) {
            subDirectories.add(subDir);
            contentSet.add(subDir.getName().v());
            if (subDir.getName().isNull())
                Lok.debug("FsDirectory.addSub");
        }
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

    public void addContent(List<GenericFSEntry> content) {
        for (GenericFSEntry genericFSEntry : content) {
            if (genericFSEntry.isDirectory.v()) {
                addSubDirectory((FsDirectory) genericFSEntry.ins());
            } else {
                addFile((FsFile) genericFSEntry.ins());
            }
        }
    }

    public FsDirectory addDummySubFsDirectory(String name) {
        addSubDirectory(new FsDirectory().setName(name));
        return this;
    }

    public FsDirectory addDummyFsFile(String name) {
        addFile(new FsFile().setName(name));
        return this;
    }

    public void removeSubFsDirecoryByName(String name) {
        if (contentSet.remove(name)) {
            List<FsDirectory> oldeSubs = subDirectories;
            subDirectories = new ArrayList<>();
            for (FsDirectory oldeSub : oldeSubs) {
                if (!oldeSub.getName().v().equals(name))
                    subDirectories.add(oldeSub);
            }
        }
    }

    public void removeFsFileByName(String name) {
        if (contentSet.remove(name)) {
            List<FsFile> oldeSubs = files;
            files = new ArrayList<>();
            for (FsFile oldeFile : oldeSubs) {
                if (!oldeFile.getName().v().equals(name))
                    files.add(oldeFile);
            }
        }
    }
}

