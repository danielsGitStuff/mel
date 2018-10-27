import de.mein.Lok;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.update.VersionAnswer;
import org.junit.Test;

public class SerTest {
    @Test
    public void serializeVersion() throws Exception {
        VersionAnswer answer = new VersionAnswer();
        answer.addEntry("hash", "name", "version");
        String json = SerializableEntitySerializer.serialize(answer);
        Lok.debug(json);
    }
}
