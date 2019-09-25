package de.mel.auth.data;

import de.mel.KResult;
import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelBoot;
import de.mel.core.serialize.JsonIgnore;

import java.io.File;
import java.security.SecureRandom;

/**
 * Created by xor on 6/10/16.
 */
public class MelAuthSettings extends JsonSettings implements KResult {
    public static final File DEFAULT_FILE = new File("mel.settings.json");
    public static final File DEFAULT_FILE_2 = new File("mel.settings.2.json");
    public static final int BROTCAST_PORT = 9966;
    public static final Integer UPDATE_MSG_PORT = 8448;
    public static final int UPDATE_BINARY_PORT = 8449;
    public static final String UPDATE_DEFAULT_URL = "xorserv.spdns.de";
    private int deliveryPort, port;
    private String workingdirectoryPath, name;
    private File workingDirectory;
    private Integer brotcastListenerPort;
    private Integer brotcastPort;
    private Class<? extends MelAuthService> melAuthServiceClass;
    private PowerManagerSettings powerManagerSettings = new PowerManagerSettings();
    private Boolean redirectSysout = false;
    private int updateMessagePort, updateBinaryPort;
    private String updateUrl, variant;
    @JsonIgnore
    private boolean headless = false;
    private String language;
    private Long preserveLogLinesInDb = 0L;

    public MelAuthSettings setVariant(String variant) {
        this.variant = variant;
        return this;
    }

    public String getVariant() {
        return variant;
    }

    public MelAuthSettings setUpdateUrl(String updateUrl) {
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

    public MelAuthSettings setUpdateBinaryPort(int updateBinaryPort) {
        this.updateBinaryPort = updateBinaryPort;
        return this;
    }

    public MelAuthSettings setUpdateMessagePort(int updateMessagePort) {
        this.updateMessagePort = updateMessagePort;
        return this;
    }

    public static MelAuthSettings createDefaultSettings() {
        MelAuthSettings melAuthSettings = new MelAuthSettings();
        melAuthSettings.setPort(8888)
                .setDeliveryPort(8889)
                .setName("Mel on PC")
                .setBrotcastListenerPort(BROTCAST_PORT)
                .setBrotcastPort(BROTCAST_PORT)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1())
                .setUpdateMessagePort(UPDATE_MSG_PORT)
                .setUpdateBinaryPort(UPDATE_BINARY_PORT)
                .setUpdateUrl(UPDATE_DEFAULT_URL)
                .setVariant(MelStrings.update.VARIANT_JAR)
                .setPreserveLogLinesInDb(0L)
                .setJsonFile(DEFAULT_FILE);
        return melAuthSettings;
    }

    public PowerManagerSettings getPowerManagerSettings() {
        return powerManagerSettings;
    }

    /**
     * TODO: rename me properly
     *
     * @param redirectSysout
     * @return
     */
    public MelAuthSettings setRedirectSysout(Boolean redirectSysout) {
        this.redirectSysout = redirectSysout;
        return this;
    }

    public MelAuthSettings setBrotcastListenerPort(Integer brotcastListenerPort) {
        this.brotcastListenerPort = brotcastListenerPort;
        return this;
    }

    public MelAuthSettings setBrotcastPort(Integer brotcastPort) {
        this.brotcastPort = brotcastPort;
        return this;
    }

    public int getBrotcastPort() {
        return brotcastPort;
    }

    public Integer getBrotcastListenerPort() {
        return brotcastListenerPort;
    }

    public MelAuthSettings() {
        Lok.debug("");
    }

    public String getDiscoverMessage() {
        return "melauth.discover(" + port + "," + deliveryPort + ")";
    }

    public String getDiscoverAnswer() {
        return "melauth.discover.answer(" + port + "," + deliveryPort + ")";
    }

    public Integer getDeliveryPort() {
        return deliveryPort;
    }

    public MelAuthSettings setDeliveryPort(int deliveryPort) {
        this.deliveryPort = deliveryPort;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public MelAuthSettings setPort(int port) {
        this.port = port;
        return this;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public MelAuthSettings setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.workingdirectoryPath = workingDirectory.getAbsolutePath();
        return this;
    }

    public String getName() {
        return name;
    }

    public MelAuthSettings setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    protected void init() {
        this.workingDirectory = new File(this.workingdirectoryPath);
    }


    /**
     * will start without JavaFX GUI
     */
    public void setHeadless() {
        this.headless = true;
    }

    public boolean isHeadless() {
        return headless;
    }

    public MelAuthSettings setLanguage(String language) {
        this.language = language;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public Long getPreserveLogLinesInDb() {
        return preserveLogLinesInDb;
    }

    public MelAuthSettings setPreserveLogLinesInDb(Long preserveLogLinesInDb) {
        this.preserveLogLinesInDb = preserveLogLinesInDb;
        return this;
    }
}
