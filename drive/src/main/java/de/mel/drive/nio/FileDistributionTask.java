package de.mel.drive.nio;

import de.mel.auth.file.AFile;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.drive.bash.FsBashDetails;

import java.util.ArrayList;
import java.util.List;

public class FileDistributionTask implements SerializableEntity {

    public enum FileDistributionState {
        NRY, // not ready yet
        READY,
        DONE
    }

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
    private Long id;
    private FileDistributionState state;

    public FileDistributionState getState() {
        return state;
    }

    public FileDistributionTask setState(FileDistributionState state) {
        this.state = state;
        return this;
    }

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

    public Long getId() {
        return id;
    }

    public FileDistributionTask setId(Long id) {
        this.id = id;
        return this;
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
        if (sourcePath != null)
            this.sourceFile = AFile.instance(sourcePath);
        return this;
    }

    public List<String> getTargetPaths() {
        return targetPaths;
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
        this.sourceFile = sourceFile;
        this.sourcePath = sourceFile.getAbsolutePath();
        return this;
    }

    public List<AFile> getTargetFiles() {
        return targetFiles;
    }

    public boolean canStart() {
        return targetFiles.size() > 0 && sourcePath != null;
    }
}
