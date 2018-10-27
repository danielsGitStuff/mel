package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.konsole.Konsole;
import de.mein.sql.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 1/15/17.
 */
public class AuthKonsoleReader {


    public static MeinAuthSettings readKonsole(String variant, String[] arguments) throws Exception {
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings().setVariant(variant);
        if (arguments.length > 0) {
            meinAuthSettings = MeinAuthSettings.createDefaultSettings();
            MeinAuthSettings finalMeinAuthSettings = meinAuthSettings;
            new Konsole<>(meinAuthSettings).optional("-bcp", "port used for broadcasting", (result, args) -> result.setBrotcastPort(Integer.parseInt(args[0])))
                    .optional("-bclp", "port to listen for broadcasts", (result, args) -> result.setBrotcastListenerPort(Integer.parseInt(args[0])))
                    .optional("-p", "port used for messaging (listening and sending)", (result, args) -> result.setPort(Integer.parseInt(args[0])))
                    .optional("-dp", "port used to deliver the certificate (unencrypted)", (result, args) -> result.setDeliveryPort(Integer.parseInt(args[0])))
                    .optional("-d", "path of working directory. certificate and key pair is stored there", (result, args) -> finalMeinAuthSettings.setWorkingDirectory(new File(args[0])))
                    .handle(arguments);
        } else if (MeinAuthSettings.DEFAULT_FILE.exists()) {
            Lok.debug("loading settings from " + MeinAuthSettings.DEFAULT_FILE.getAbsolutePath());
            meinAuthSettings = (MeinAuthSettings) MeinAuthSettings.load(MeinAuthSettings.DEFAULT_FILE);
        }
        return meinAuthSettings;
    }
}
