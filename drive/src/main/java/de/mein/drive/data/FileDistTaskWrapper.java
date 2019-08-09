package de.mein.drive.data;

import java.util.ArrayList;
import java.util.List;

import de.mein.Lok;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.drive.nio.FileDistributionTask;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

public class FileDistTaskWrapper extends SQLTableObject {

    public static class FileWrapper extends SQLTableObject {
        private Pair<String> targetPath = new Pair<>(String.class, "tpath");
        private Pair<Long> targetFsId = new Pair<>(Long.class, "tfsid");
        private Pair<Long> id = new Pair<>(Long.class, "id");
        private Pair<Long> taskId = new Pair<>(Long.class, "taskid");


        public FileWrapper(String path, Long fsId) {
            init();
            targetPath.v(path);
            targetFsId.v(fsId);
        }

        public FileWrapper() {
            init();
        }

        @Override
        public String getTableName() {
            return "filedisttargets";
        }

        @Override
        protected void init() {
            populateInsert(targetFsId, targetPath, taskId);
            populateAll(id);
        }

        public Pair<Long> getTargetFsId() {
            return targetFsId;
        }

        public Pair<String> getTargetPath() {
            return targetPath;
        }

        public Pair<Long> getTaskId() {
            return taskId;
        }

        public Pair<Long> getId() {
            return id;
        }
    }

    private Pair<String> serviceUuid = new Pair<>(String.class, "uuid");
    private Pair<Boolean> deleteSource = new Pair<>(Boolean.class, "deletesource");

    private Pair<String> sourcePath = new Pair<>(String.class, "sourcepath");
    private Pair<String> sourceHash = new Pair<>(String.class, "sourcehash");
    private Pair<String> sourceDetails = new Pair<>(String.class, "sourcedetails");

    private Pair<Long> size = new Pair<>(Long.class, "fsize");
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<Boolean> done = new Pair<>(Boolean.class, "done", false);

    private List<FileWrapper> targetWraps = new ArrayList<>();

    public static FileDistTaskWrapper fromTask(FileDistributionTask task) {
        FileDistTaskWrapper wrapper = new FileDistTaskWrapper();
        wrapper.serviceUuid.v(task.getServiceUuid());
        wrapper.sourcePath.v(task.getSourcePath());
        wrapper.sourceHash.v(task.getSourceHash());
        wrapper.sourceDetails.v(N.result(() -> SerializableEntitySerializer.serialize(task.getSourceDetails()), null));
        if (wrapper.sourceDetails.isNull()) {
            Lok.error("could not serialize source details");
        }
        wrapper.deleteSource.v(task.getDeleteSource());
        wrapper.size.v(task.getSize());

        for (int i = 0; i < task.getTargetPaths().size(); i++) {
            String path = task.getTargetPaths().get(i);
            Long fsId = task.getTargetFsIds().get(0);
            wrapper.targetWraps.add(new FileWrapper(path, fsId));
        }
        return wrapper;
    }

    public FileDistTaskWrapper() {
        init();
    }

    public Pair<String> getServiceUuid() {
        return serviceUuid;
    }

    public Pair<Boolean> getDeleteSource() {
        return deleteSource;
    }

    public Pair<String> getSourcePath() {
        return sourcePath;
    }

    public Pair<String> getSourceHash() {
        return sourceHash;
    }

    public Pair<Long> getSize() {
        return size;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<Boolean> getDone() {
        return done;
    }

    public List<FileWrapper> getTargetWraps() {
        return targetWraps;
    }

    @Override
    public String getTableName() {
        return "filedist";
    }

    public Pair<String> getSourceDetails() {
        return sourceDetails;
    }

    @Override
    protected void init() {
        populateInsert(sourceHash, sourcePath, sourceDetails, serviceUuid, deleteSource, size, done);
        populateAll(id);
        id.setSetListener(value -> {
            N.forEach(targetWraps, fileWrapper -> fileWrapper.taskId.v(value));
            return value;
        });
    }
}
