package de.mein.auth.data;

import de.mein.auth.MeinStrings;
import de.mein.auth.service.IDBCreatedListener;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.konsole.KResult;

import java.io.File;
import java.security.SecureRandom;

/**
 * Created by xor on 6/10/16.
 */
public class MeinAuthSettings extends JsonSettings implements KResult {
    public static final File DEFAULT_FILE = new File("settings.json");
    public static final int BROTCAST_PORT = 9966;
    public static final Integer UPDATE_MSG_PORT = 8956;
    public static final int UPDATE_BINARY_PORT = 8957;
    public static final String UPDATE_DEFAULT_URL = "127.0.0.1";
    private int deliveryPort, port;
    private String workingdirectoryPath, name;
    private File workingDirectory;
    private String greeting;
    private Integer brotcastListenerPort;
    private Integer brotcastPort;
    private Class<? extends MeinAuthService> meinAuthServiceClass;
    private IDBCreatedListener idbCreatedListener;
    private PowerManagerSettings powerManagerSettings = new PowerManagerSettings();
    private Boolean redirectSysout = false;
    private int updateMessagePort, updateBinaryPort;
    private String updateUrl, variant;

    public MeinAuthSettings setVariant(String variant) {
        this.variant = variant;
        return this;
    }

    public String getVariant() {
        return variant;
    }

    public MeinAuthSettings setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
        return this;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public int getUpdateMessagePort() {
        return updateMessagePort;
    }

    public int getUpdateBinaryPort() {
        return updateBinaryPort;
    }

    public MeinAuthSettings setUpdateBinaryPort(int updateBinaryPort) {
        this.updateBinaryPort = updateBinaryPort;
        return this;
    }

    public MeinAuthSettings setUpdateMessagePort(int updateMessagePort) {
        this.updateMessagePort = updateMessagePort;
        return this;
    }

    public static MeinAuthSettings createDefaultSettings() {
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings();
        meinAuthSettings.setPort(8888)
                .setDeliveryPort(8889)
                .setName("meinauth")
                .setBrotcastListenerPort(BROTCAST_PORT)
                .setBrotcastPort(BROTCAST_PORT)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1)
                .setGreeting(generateGreeting())
                .setUpdateMessagePort(UPDATE_MSG_PORT)
                .setUpdateBinaryPort(UPDATE_BINARY_PORT)
                .setUpdateUrl(UPDATE_DEFAULT_URL)
                .setVariant(MeinStrings.update.VARIANT_JAR)
                .setJsonFile(DEFAULT_FILE);
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

    /**
     * TODO: rename me properly
     *
     * @param redirectSysout
     * @return
     */
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
        return workingDirectory;
    }

    public MeinAuthSettings setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.workingdirectoryPath = workingDirectory.getAbsolutePath();
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

    @Override
    protected void init() {
        this.workingDirectory = new File(this.workingdirectoryPath);
    }


}
