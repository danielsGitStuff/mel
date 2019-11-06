package de.mel.filesync.data.fs;

import de.mel.auth.file.AbstractFile;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.File;

public class RootDirectory implements SerializableEntity {

    private String path;
    @JsonIgnore
    private RootDirectory backup;
    private AbstractFile originalFile;
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

    public RootDirectory setOriginalFile(AbstractFile originalFile) {
        this.originalFile = originalFile;
        return this;
    }

    public Long getId() {
        return id;
    }

    public RootDirectory setId(Long id) {
        this.id = id;
        return this;
    }

    public AbstractFile getOriginalFile() {
        return AbstractFile.instance(path);
    }
}
