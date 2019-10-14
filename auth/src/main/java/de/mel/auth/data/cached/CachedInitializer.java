package de.mel.auth.data.cached;

import de.mel.Lok;
import de.mel.auth.data.ServicePayload;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * This is the first part of a cached data structure.
 * Extend this class and put it as {@link ServicePayload} in a {@link de.mel.auth.data.MelRequest}, {@link de.mel.auth.data.MelResponse} or {@link de.mel.auth.data.MelMessage}.
 * If the structure holds elements 'part' is not null.
 *
 * @param <P> Type of the {@link CachedPart}s contained in this instance.
 */
public abstract class CachedInitializer<P extends CachedPart> extends ServicePayload {
    protected int partCount = 0;
    protected int partSize = 2000;
    protected Long cacheId;
    protected File cacheDir;
    @JsonIgnore
    protected Set<Integer> partsMissed;

    protected P part;

    public P getPart() {
        return part;
    }

    public CachedInitializer() {

    }

    public void setCacheDir(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public CachedInitializer(File cacheDir, Long cacheId, int partSize) {
        this.cacheDir = cacheDir;
        this.cacheId = cacheId;
        this.partSize = partSize;
    }

    public int getPartCount() {
        return partCount;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public Long getCacheId() {
        return cacheId;
    }

    public File createCachedPartFile(int partCount) {
        return new File(cacheDir.getAbsolutePath() + File.separator + cacheId + "." + partCount + ".json");
    }

    /**
     * writes everything to disk to save memory.
     * call this when done with adding all elements.
     */
    public abstract void toDisk() throws JsonSerializationException, IOException;

    public void cleanUp() {
//        Lok.debug("CachedData.cleanUp(). SKIPPING DELETE for debug reaons");
        part = null;
        partCount = 0;
        for (int i = 0; i < partCount; i++) {
            File f = createCachedPartFile(i);
            try {
                if (f.exists())
                    f.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * serializes to disk
     *
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     * @throws IOException
     */
    protected void write(CachedPart part) throws JsonSerializationException, IOException {
        //serialize actual list, create a new one
        part.setSerialized();
        String json = SerializableEntitySerializer.serialize(part);
        //save to file
        File file = createCachedPartFile(part.getPartNumber());
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        out.write(json.getBytes());
        out.close();
    }

    public void onReceivedPart(CachedPart cachedPart) throws JsonSerializationException, IOException {
        write(cachedPart);
        if (partsMissed == null)
            Lok.debug("debu4g");
        partsMissed.remove(cachedPart.getPartNumber());
//        partCount++;
    }

    public CachedPart getPart(int partNumber) throws IOException, JsonDeserializationException {
        CachedPart part = CachedPart.read(createCachedPartFile(partNumber));
        return part;
    }

    public void initPartsMissed() {
        this.initPartsMissed(partCount);
    }

    public void initPartsMissed(int partCount) {
        this.partCount = partCount;
        partsMissed = new HashSet<>();
        int skip = -1;
        if (part != null)
            skip = part.getPartNumber();
        for (int i = 1; i <= partCount; i++) {
            if (i != skip)
                partsMissed.add(i);
        }
    }

    public boolean isComplete() {
        return partsMissed != null && partsMissed.size() == 0;
    }

    public int getNextPartNumber() {
        return partsMissed.iterator().next();
    }

    public static Long randomId() {
        return new SecureRandom().nextLong();
    }
}
