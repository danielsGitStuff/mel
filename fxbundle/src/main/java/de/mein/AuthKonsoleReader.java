package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.konsole.Konsole;

import java.io.File;

/**
 * Created by xor on 1/15/17.
 */
public class AuthKonsoleReader {


    public static MeinAuthSettings readKonsole(String variant, String[] arguments) throws Exception {
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings().setVariant(variant);
        if (MeinAuthSettings.DEFAULT_FILE.exists()) {
            Lok.debug("loading settings from " + MeinAuthSettings.DEFAULT_FILE.getAbsolutePath());
            meinAuthSettings = (MeinAuthSettings) MeinAuthSettings.load(MeinAuthSettings.DEFAULT_FILE);
        }
        if (arguments.length > 0) {
            meinAuthSettings = MeinAuthSettings.createDefaultSettings();
            new Konsole<>(meinAuthSettings).optional("-bcp", "port used for broadcasting", (result, args) -> result.setBrotcastPort(Integer.parseInt(args[0])))
                    .optional("-bclp", "port to listen for broadcasts", (result, args) -> result.setBrotcastListenerPort(Integer.parseInt(args[0])))
                    .optional("-p", "port used for messaging (listening and sending)", (result, args) -> result.setPort(Integer.parseInt(args[0])))
                    .optional("-dp", "port used to deliver the certificate (unencrypted)", (result, args) -> result.setDeliveryPort(Integer.parseInt(args[0])))
                    .optional("-d", "path of working directory. certificate and key pair is stored there", (result, args) -> result.setWorkingDirectory(new File(args[0])))
                    .optional("-headless", "starts without JavaFx GUI", (result, args) -> result.setHeadless())
                    .optional("-dev", "for dev purposes only", (result, args) -> Lok.error("DEV DEV DEV DEV DEV"))
                    .optional("-name", "name for this instance", (result, args) -> result.setName(args[0]))
                    .optional("-logtodb", "[preservedLines]: logs into 'log.db' in the working directory. preservedLines: keep at leat so many line", (result, args) -> {
                        if (args.length > 0 && args[0] != null) {
                            Long value = Long.parseLong(args[0]);
                            if (value < 0L) {
                                Lok.error("-logtodb [value] error: value was smaller the zero");
                                System.exit(-1);
                            }
                            result.setPreserveLogLinesInDb(value);
                        } else {
                            result.setPreserveLogLinesInDb(1000L);
                        }
                    })
                    .handle(arguments);
        }
        return meinAuthSettings;
    }
}
