package de.mein.drive.data;

import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.drive.nio.FileDistributionTask;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

public class FileDistTaskWrapper extends SQLTableObject {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> json = new Pair<>(String.class, "json");
    private Pair<Boolean> done = new Pair<>(Boolean.class, "done", false);
    private FileDistributionTask task;

    public FileDistTaskWrapper() {
        init();
    }

    public FileDistTaskWrapper(FileDistributionTask task) {
        this.task = task;
        init();
    }

    @Override
    public String getTableName() {
        return "filedist";
    }

    @Override
    protected void init() {
        populateAll(json, done);
        populateInsert(id);
        json.setGetListener(() -> {
            if (task != null) {
                json.v(N.result(() -> SerializableEntitySerializer.serialize(task), "serialization failed"));
            }
        });
    }

    public Pair<String> getJson() {
        return json;
    }

    public FileDistributionTask getTask() {
        if (task == null && json.notNull()) {
            task = N.result(() -> task = (FileDistributionTask) SerializableEntityDeserializer.deserialize(json.ignoreListener().v()), null);
        }
        return task;
    }

    public Pair<Boolean> getDone() {
        return done;
    }

    public Pair<Long> getId() {
        return id;
    }
}
