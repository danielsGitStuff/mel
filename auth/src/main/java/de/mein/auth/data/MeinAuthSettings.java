package de.mein.auth.data;

import de.mein.auth.service.IDBCreatedListener;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;

import java.io.File;
import java.security.SecureRandom;

/**
 * Created by xor on 6/10/16.
 */
public class MeinAuthSettings extends JsonSettings {
    public static final File DEFAULT_FILE = new File("meinauthsettings.json");
    public static final int BROTCAST_PORT = 9966;
    private int deliveryPort, port;
    private String workingDirectory, name;
    private String greeting;
    private Integer brotcastListenerPort;
    private Integer brotcastPort;
    private Class<? extends MeinAuthService> meinAuthServiceClass;
    private IDBCreatedListener idbCreatedListener;
    private PowerManagerSettings powerManagerSettings = new PowerManagerSettings();
    private Boolean redirectSysout = false;

    public static MeinAuthSettings createDefaultSettings() {
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings();
        meinAuthSettings.setPort(8888)
                .setDeliveryPort(8889)
                .setName("meinauth")
                .setBrotcastListenerPort(BROTCAST_PORT)
                .setBrotcastPort(BROTCAST_PORT)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1)
                .setGreeting(generateGreeting())
                .setJsonFile(new File("meinAuth.settings.json"));
        return meinAuthSettings;
    }

    public PowerManagerSettings getPowerManagerSettings() {
        return powerManagerSettings;
    }

    public Boolean getRedirectSysout() {
        //todo return redirectSysout only
        if (redirectSysout == null)
            return false;
        return redirectSysout;
    }

    public MeinAuthSettings setRedirectSysout(Boolean redirectSysout) {
        this.redirectSysout = redirectSysout;
        return this;
    }

    private static String generateGreeting() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvw1234567890";
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[6];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) chars.charAt(random.nextInt(chars.length()));
        }
        return new String(bytes);
    }

    public MeinAuthSettings setBrotcastListenerPort(Integer brotcastListenerPort) {
        this.brotcastListenerPort = brotcastListenerPort;
        return this;
    }


    public MeinAuthSettings setMeinAuthServiceClass(Class<? extends MeinAuthService> meinAuthServiceClass) {
        this.meinAuthServiceClass = meinAuthServiceClass;
        return this;
    }

    public MeinAuthSettings setIdbCreatedListener(IDBCreatedListener idbCreatedListener) {
        this.idbCreatedListener = idbCreatedListener;
        return this;
    }

    public IDBCreatedListener getIdbCreatedListener() {
        return idbCreatedListener;
    }

    public Class<? extends MeinAuthService> getMeinAuthServiceClass() {
        return meinAuthServiceClass;
    }

    public MeinAuthSettings setBrotcastPort(Integer brotcastPort) {
        this.brotcastPort = brotcastPort;
        return this;
    }

    public int getBrotcastPort() {
        return brotcastPort;
    }

    public Integer getBrotcastListenerPort() {
        return brotcastListenerPort;
    }

    public MeinAuthSettings() {
    }

    public String getDiscoverMessage() {
        return "meinauth.discover(" + port + "," + deliveryPort + ")";
    }

    public String getDiscoverAnswer() {
        return "meinauth.discover.answer(" + port + "," + deliveryPort + ")";
    }

    public String getGreeting() {
        return greeting;
    }

    public MeinAuthSettings setGreeting(String greeting) {
        this.greeting = greeting;
        return this;
    }

    public Integer getDeliveryPort() {
        return deliveryPort;
    }

    public MeinAuthSettings setDeliveryPort(int deliveryPort) {
        this.deliveryPort = deliveryPort;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public MeinAuthSettings setPort(int port) {
        this.port = port;
        return this;
    }

    public File getWorkingDirectory() {
        return new File(workingDirectory);
    }

    public MeinAuthSettings setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory.getAbsolutePath();
        File jsonFile = new File(workingDirectory.getAbsolutePath() + File.separator + DEFAULT_FILE);
        this.setJsonFile(jsonFile);
        return this;
    }

    public String getName() {
        return name;
    }

    public MeinAuthSettings setName(String name) {
        this.name = name;
        return this;
    }
}
