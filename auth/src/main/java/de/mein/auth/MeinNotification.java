package de.mein.auth;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 08.08.2017.
 */
public class MeinNotification {
    private String title;
    private String text;
    private Object content;
    private final String serviceUuid;
    private final String intention;
    private Map<String, Object> extras = new HashMap<>();
    // progress related stuff
    private List<MeinProgressListener> progressListeners = new ArrayList<>();
    private boolean indeterminate = false;
    private int current = 0;
    private int max = 0;
    private boolean canceled = false;
    private boolean finished = false;

    public void cancel() {
        canceled = true;
        for (MeinProgressListener listener : progressListeners) {
            listener.cancel();
        }
    }

    public MeinNotification setText(String text) {
        this.text = text;
        return this;
    }

    public MeinNotification setTitle(String title) {
        this.title = title;
        return this;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void finish() {
        finished = true;
        for (MeinProgressListener listener : progressListeners) {
            listener.finish();
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public interface MeinProgressListener {
        void onProgress(int max, int current, boolean indeterminate);

        void cancel();

        void finish();
    }

    /**
     * @param serviceUuid source of the notification
     * @param intention   tell the consumer of the notification what to do
     * @param title
     * @param text
     */
    public MeinNotification(String serviceUuid, String intention, String title, String text) {
        this.serviceUuid = serviceUuid;
        this.intention = intention;
        this.text = text;
        this.title = title;
    }

    public MeinNotification(String serviceUuid, String intention, String title, String text, Object content) {
        this(serviceUuid, intention, title, text);
        this.content = content;
    }

    public String getText() {
        return text;
    }

    public String getTitle() {
        return title;
    }

    /**
     * if you want consume a data object immediately(eg. fill a table with its content), this is where to put it.
     *
     * @param dataObject
     */
    public void setContent(Object dataObject) {
        this.content = dataObject;
    }

    public Object getContent() {
        return content;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public String getIntention() {
        return intention;
    }

    /**
     * If you want to consume a data object later(eg. fill a table with its content) you can store it as an extra here. Note: The extra will be serialized.<br>
     * Android likes this.
     *
     * @param extra
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     */
    public void addSerializedExtra(String key, SerializableEntity extra) throws JsonSerializationException, IllegalAccessException {
        String json = SerializableEntitySerializer.serialize(extra);
        extras.put(key, json);
    }

    public SerializableEntity getSerializedExtra(String key) throws JsonDeserializationException {
        return SerializableEntityDeserializer.deserialize((String) extras.get(key));
    }

    public Object getExtra(String key) {
        return extras.get(key);
    }

    public MeinNotification addExtra(String key, Object value) {
        extras.put(key, value);
        return this;
    }

    public void addProgressListener(MeinProgressListener progressListener) {
        progressListeners.add(progressListener);
    }

    public MeinNotification setProgress(int max, int current, boolean indeterminate) {
        this.max = max;
        this.current = current;
        this.indeterminate = indeterminate;
        for (MeinProgressListener listener : progressListeners)
            listener.onProgress(max, current, indeterminate);
        return this;
    }

    public int getMax() {
        return max;
    }

    public int getCurrent() {
        return current;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }
}
