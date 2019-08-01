package de.mein.drive.nio;

import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.drive.bash.FsBashDetails;

import java.util.ArrayList;
import java.util.List;

public class FileDistributionTask implements SerializableEntity {

    private String sourcePath;
    private AFile sourceFile;
    private List<String> targetPaths = new ArrayList<>();
    private List<AFile> targetFiles = new ArrayList<>();
    private List<Long> targetFsIds = new ArrayList<>();
    private Boolean deleteSource;
    private boolean hasOptionals = false;
    private FsBashDetails sourceDetails;
    private Long size;


    public List<Long> getTargetFsIds() {
        return targetFsIds;
    }

    public void setOptionals(FsBashDetails bashDetails, long size) {
        this.size = size;
        this.sourceDetails = bashDetails;
        hasOptionals = true;
    }

    public void setDeleteSource(Boolean deleteSource) {
        this.deleteSource = deleteSource;
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

    public FileDistributionTask initFromPaths() {
        this.targetFiles = new ArrayList<>();
        N.forEach(targetPaths, AFile::instance);
        this.sourceFile = AFile.instance(sourcePath);
        return this;
    }

    public FileDistributionTask setSourceFile(AFile sourceFile) {
        this.sourceFile = sourceFile;
        this.sourcePath = sourceFile.getAbsolutePath();
        return this;
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

    public List<AFile> getTargetFiles() {
        return targetFiles;
    }
}
