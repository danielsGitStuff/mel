package de.mein.auth;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

/**
 * Created by xor on 08.08.2017.
 */
public class MeinNotification {
    private final String title;
    private final String text;
    private Object content;
    private final String serviceUuid;
    private final String intention;
    private String serializedExtra;

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
     * @param extra
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     */
    public void setExtra(SerializableEntity extra) throws JsonSerializationException, IllegalAccessException {
        this.serializedExtra = SerializableEntitySerializer.serialize(extra);
    }

    public String getExtra() {
        return serializedExtra;
    }
}
