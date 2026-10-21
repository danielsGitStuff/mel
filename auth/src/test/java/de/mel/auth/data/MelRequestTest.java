package de.mel.auth.data;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MelRequestTest {
    private static Set<String> timerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("Timer-") || name.startsWith("WatchDogTimer") || name.startsWith("request"))
                .collect(Collectors.toSet());
    }

    @Test
    public void requestsThatWereOnlyCreatedOrDeserializedStartNoThreads() throws Exception {
        int before = Thread.activeCount();
        Set<String> timersBefore = timerThreads();
        for (int i = 0; i < 1000; i++) {
            MelRequest request = new MelRequest("service", "intent");
            String json = SerializableEntitySerializer.serialize(request);
            SerializableEntity back = SerializableEntityDeserializer.deserialize(json);
            assertTrue(back instanceof MelRequest);
        }
        assertEquals(timersBefore, timerThreads(), "creating or deserializing requests must not start timer threads");
        assertTrue(Thread.activeCount() <= before + 2, "threads before " + before + ", after " + Thread.activeCount());
    }

    @Test
    public void aRequestSurvivesTheRoundTripWithoutItsTimeout() throws Exception {
        MelRequest request = new MelRequest("service", "intent");
        String json = SerializableEntitySerializer.serialize(request);
        assertFalse(json.contains("timeout"), json);
        assertFalse(json.contains("answerDeferred"), json);
        MelRequest back = (MelRequest) SerializableEntityDeserializer.deserialize(json);
        assertEquals(request.getRequestId(), back.getRequestId());
    }
}
