package de.mel.serialization;

import de.mel.auth.data.MelResponse;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.filesync.service.sync.TooOldVersionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerTest {
    @Test
    public void testResponseException() throws Exception {
        TooOldVersionException exception = new TooOldVersionException("bla", 77L);
        MelResponse response = new MelResponse();
        response.setException(exception);
        String json = new SerializableEntitySerializer().setEntity(response).JSON();
        System.out.println(json);

        MelResponse deserialized = (MelResponse) SerializableEntityDeserializer.deserialize(json);
        System.out.println("SerTest.testResponseException");
        TooOldVersionException ex2 = (TooOldVersionException) deserialized.getException();
        assertEquals(response.getException().getMessage(), deserialized.getException().getMessage());
        assertEquals(exception.getNewVersion(), ex2.getNewVersion());
        assertEquals(exception.getCause(), ex2.getCause());
    }
}
