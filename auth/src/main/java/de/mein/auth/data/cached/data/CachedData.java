package de.mein.auth.data.cached.data;

import de.mein.auth.data.IPayload;
import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

/**
 * The overall class for anything which is cached. Its name is required to identify as a certain cached object.
 * Tells you how many parts the data is divided to.
 */
public abstract class CachedData implements IPayload {
    protected Long cacheId;
    protected int partCount = 1;
    protected CachedPart part;
    protected File cacheDir;
    protected int partSize;

    @JsonIgnore
    protected Set<Integer> partsMissed;
    @JsonIgnore
    private String serviceUuid;


    public CachedData(int partSize) {
        this.partSize = partSize;
    }

    public CachedData() {
    }

    public CachedData setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public Long getCacheId() {
        return cacheId;
    }

    public CachedData setCacheId(Long cacheId) {
        this.cacheId = cacheId;
        if (part != null)
            part.setCacheId(cacheId);
        return this;
    }

    public boolean isStillInMemory() {
        return !(partCount > 1);
    }

    public int getPartCount() {
        return partCount;
    }

    public void initPartsMissed(int amount) {
        partsMissed = new HashSet<>();
        for (int i = 1; i <= amount; i++) {
            partsMissed.add(amount);
        }
    }


    /**
     * serializes to disk
     *
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     * @throws IOException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
    protected void write(CachedPart part) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        //serialize actual list, create a new one
        part.setSerialized();
        String json = SerializableEntitySerializer.serialize(part);
        //save to file
        File file = createCachedPartFile(part.getPartNumber());
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        out.write(json.getBytes());
        out.close();
    }


    public File createCachedPartFile(int partCount) {
        return new File(cacheDir.getAbsolutePath() + File.separator + cacheId + "." + partCount + ".json");
    }

    public void setCacheDirectory(File cacheDirectory) {
        this.cacheDir = cacheDirectory;
    }

    public CachedPart getPart() {
        return part;
    }

    public void onReceivedPart(CachedPart cachedPart) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        write(cachedPart);
        partsMissed.remove(cachedPart.getPartNumber());
    }

    public boolean isComplete() {
        return partsMissed.size() == 0;
    }

    public void cleanUp() {
        part = null;
        partCount = 0;
        for (int i = 0; i < partCount; i++) {
            File f = createCachedPartFile(i);
            try {
                // if (f.exists())
                //   f.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public int getNextPartNumber() {
        return partsMissed.iterator().next();
    }

    public CachedPart getPart(int partNumber) throws IOException, JsonDeserializationException {
        CachedPart part = CachedPart.read(createCachedPartFile(partCount));
        return part;
    }
}
