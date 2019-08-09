package de.mein.drive.nio;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.drive.bash.FsBashDetails;

import java.util.ArrayList;
import java.util.List;

public class FileDistributionTask implements SerializableEntity {

    private String sourcePath;
    private AFile sourceFile;
    private String sourceHash;
    private List<String> targetPaths = new ArrayList<>();
    private List<AFile> targetFiles = new ArrayList<>();
    private List<Long> targetFsIds = new ArrayList<>();
    private Boolean deleteSource;
    private boolean hasOptionals = false;
    private FsBashDetails sourceDetails;
    private Long size;
    // uuid of the service that created the task.
    // this is because you can have only one Service at a time on Android
    // and you got to get the appropriate FsDao when moving files
    private String serviceUuid;

    public String getSourcePath() {
        return sourcePath;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public FileDistributionTask setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
        return this;
    }

    public List<Long> getTargetFsIds() {
        return targetFsIds;
    }

    public void setOptionals(FsBashDetails bashDetails, long size) {
        this.size = size;
        this.sourceDetails = bashDetails;
        hasOptionals = true;
    }

    public FsBashDetails getSourceDetails() {
        return sourceDetails;
    }

    public Long getSize() {
        return size;
    }

    public boolean hasOptionals() {
        return hasOptionals;
    }

    public Boolean getDeleteSource() {
        return deleteSource;
    }

    public void setDeleteSource(Boolean deleteSource) {
        this.deleteSource = deleteSource;
    }

    /**
     * this recreates all Files from the stored paths
     *
     * @return
     */
    public FileDistributionTask initFromPaths() {
        this.targetFiles = new ArrayList<>();
        N.forEach(targetPaths, path -> targetFiles.add(AFile.instance(path)));
        this.sourceFile = AFile.instance(sourcePath);
        return this;
    }

    public List<String> getTargetPaths() {
        return targetPaths;
    }

    /**
     * gets the serviceuuid that is associated with the file.
     * because the according FsDao is required to set the sync flag.
     */
    public String getServiceUuid() {
        return serviceUuid;
    }

    /**
     * sets the serviceuuid that is associated with the file.
     * because the according FsDao is required to set the sync flag.
     *
     * @param serviceUuid
     */
    public void setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public FileDistributionTask addTargetFile(AFile targetFile, long fsId) {
        targetFiles.add(targetFile);
        targetPaths.add(targetFile.getAbsolutePath());
        targetFsIds.add(fsId);
        return this;
    }

    public AFile getSourceFile() {
        return sourceFile;
    }

    public FileDistributionTask setSourceFile(AFile sourceFile) {
        // todo debug
        if (!sourceFile.exists())
            Lok.debug();
        if (sourceFile.getName().equals("wastebin"))
            Lok.debug();
        this.sourceFile = sourceFile;
        this.sourcePath = sourceFile.getAbsolutePath();
        return this;
    }

    public List<AFile> getTargetFiles() {
        return targetFiles;
    }
}
