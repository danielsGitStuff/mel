package de.mein.drive.data.fs;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.File;

public class RootDirectory implements SerializableEntity {

    private String path;
    @JsonIgnore
    private RootDirectory backup;
    private File originalFile;
    private Long id;


    public String getPath() {
        return path;
    }

    public RootDirectory setPath(String path) {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.path = path;

        return this;
    }

    /**
     * creates a backup so you can see if something changed
     *
     * @return the same object, not the backup
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     * @throws JsonDeserializationException
     */
    public RootDirectory backup() throws JsonSerializationException, IllegalAccessException, JsonDeserializationException {
        String json = SerializableEntitySerializer.serialize(this);
        this.backup = (RootDirectory) SerializableEntityDeserializer.deserialize(json);
        return this;
    }

    public void setOriginalFile(File originalFile) {
        this.originalFile = originalFile;
    }

    public Long getId() {
        return id;
    }

    public RootDirectory setId(Long id) {
        this.id = id;
        return this;
    }

    public File getOriginalFile() {
        return originalFile;
    }
}
