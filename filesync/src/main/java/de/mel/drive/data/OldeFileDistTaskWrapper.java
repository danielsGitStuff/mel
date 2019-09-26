package de.mel.drive.data;

import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.drive.nio.FileDistributionTask;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

public class OldeFileDistTaskWrapper extends SQLTableObject {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> json = new Pair<>(String.class, "json");
    private Pair<Boolean> done = new Pair<>(Boolean.class, "done", false);
    private FileDistributionTask task;

    public OldeFileDistTaskWrapper() {
        init();
    }

    public OldeFileDistTaskWrapper(FileDistributionTask task) {
        this.task = task;
        init();
    }

    @Override
    public String getTableName() {
        return "filedist";
    }

    @Override
    protected void init() {
        populateInsert(json, done);
        populateAll(id);
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
