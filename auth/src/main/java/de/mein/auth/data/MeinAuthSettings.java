package de.mein.auth.data;

import de.mein.auth.service.MeinAuthService;

import java.io.File;

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

    public MeinAuthSettings setBrotcastListenerPort(Integer brotcastListenerPort) {
        this.brotcastListenerPort = brotcastListenerPort;
        return this;
    }

    public MeinAuthSettings setMeinAuthServiceClass(Class<? extends MeinAuthService> meinAuthServiceClass) {
        this.meinAuthServiceClass = meinAuthServiceClass;
        return this;
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

    public int getDeliveryPort() {
        return deliveryPort;
    }

    public MeinAuthSettings setDeliveryPort(int deliveryPort) {
        this.deliveryPort = deliveryPort;
        return this;
    }

    public int getPort() {
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
