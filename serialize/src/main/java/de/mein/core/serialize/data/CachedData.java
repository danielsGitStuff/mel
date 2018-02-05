package de.mein.core.serialize.data;

import de.mein.core.serialize.JsonIgnore;
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
public abstract class CachedData {
    protected String name;
    protected int partCount = 1;
    protected CachedPart part;
    protected File cacheDir;
    @JsonIgnore
    protected Set<Integer> partsMissed;


    public CachedData(String name) {
        this.name = name;
    }

    public CachedData() {
    }

    public String getName() {
        return name;
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
     * serializes to disk and sets part to null
     *
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     * @throws IOException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
    protected void serializePart(CachedPart part) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        //serialize actual list, create a new one
        String json = SerializableEntitySerializer.serialize(part);
        //save to file
        File file = createCachedPartFile(part.getPartNumber());
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        out.write(json.getBytes());
        out.close();
    }


    public File createCachedPartFile(int partCount) {
        return new File(cacheDir.getAbsolutePath() + File.separator + name + "." + partCount + ".json");
    }

    public abstract void setCacheDirectory(File cacheDirectory);

    public CachedPart getPart() {
        return part;
    }

    public void onReceivedPart(CachedPart cachedPart) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        serializePart(cachedPart);
        partsMissed.remove(cachedPart.getPartNumber());
    }

    public boolean isComplete() {
        return partsMissed.size() == 0;
    }
}
