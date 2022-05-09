package de.mel.filesync.data;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.File;
import java.io.IOException;

public class RootDirectory implements SerializableEntity {

    private String path;
    @JsonIgnore
    private RootDirectory backup;
    private IFile originalFile;
    private Long id;

    public RootDirectory() {

    }

    public static RootDirectory buildRootDirectory(File rootFile) throws IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        return buildRootDirectory(AbstractFile.instance(rootFile));
    }

    public static RootDirectory buildRootDirectory(IFile rootFile) throws IllegalAccessException, JsonSerializationException, JsonDeserializationException, IOException {
        String path = rootFile.getCanonicalPath();
        RootDirectory rootDirectory = new RootDirectory().setPath(path);
        rootDirectory.setOriginalFile(rootFile);
        rootDirectory.backup();
        return rootDirectory;
    }

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
//        String json = SerializableEntitySerializer.serialize(this);
//        this.backup = (RootDirectory) SerializableEntityDeserializer.deserialize(json);
        return this;
    }

    public RootDirectory setOriginalFile(IFile originalFile) {
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

    public IFile getOriginalFile() {
        return AbstractFile.instance(path);
    }
}
