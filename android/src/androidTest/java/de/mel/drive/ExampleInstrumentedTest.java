package de.mel.drive;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.InputStream;

import de.mel.Lok;
import de.mel.auth.data.MelAuthSettings;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;

import static org.junit.Assert.*;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("mel.de.meldrive", appContext.getPackageName());
    }

    @Test
    public void deserialize() throws Exception {
        String json = "{\n" +
                "  \"$id\": 1,\n" +
                "  \"__type\": \"de.mel.auth.data.MelAuthSettings\",\n" +
                "  \"brotcastListenerPort\": 9966,\n" +
                "  \"updateBinaryPort\": 8449,\n" +
                "  \"deliveryPort\": 8889,\n" +
                "  \"workingdirectoryPath\": \"\\/data\\/user\\/0\\/de.mel.mel\\/melauth.workingdir\",\n" +
                "  \"port\": 8888,\n" +
                "  \"updateUrl\": \"xorserv.spdns.de\",\n" +
                "  \"name\": \"Mel on Android\",\n" +
                "  \"variant\": \"apk\",\n" +
                "  \"powerManagerSettings\": {\n" +
                "    \"$id\": 2,\n" +
                "    \"__type\": \"de.mel.auth.data.PowerManagerSettings\",\n" +
                "    \"heavyWorkWhenPlugged\": \"true\",\n" +
                "    \"heavyWorkWhenOffline\": \"true\"\n" +
                "  },\n" +
                "  \"updateMessagePort\": 8448,\n" +
                "  \"preserveLogLinesInDb\": 0,\n" +
                "  \"redirectSysout\": \"false\",\n" +
                "  \"brotcastPort\": 9966\n" +
                "}";
        Lok.debug(json);
        MelAuthSettings melAuthSettings = (MelAuthSettings) SerializableEntityDeserializer.deserialize(json);
        Lok.debug(melAuthSettings.getDeliveryPort());
        assertEquals(new Long(0L), melAuthSettings.getPreserveLogLinesInDb());
    }
}
