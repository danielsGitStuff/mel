package de.miniserver;

import de.mein.konsole.KResult;

import java.util.HashMap;
import java.util.Map;

public class ServerConfig extends KResult {
    private String certPath;
    private String workingDirectory;
    private String pubKeyPath;
    private String certName;

    public String getPubKeyPath() {
        return pubKeyPath;
    }

    public void setPubKeyPath(String pubKeyPath) {
        this.pubKeyPath = pubKeyPath;
    }

    public String getPrivKeyPath() {
        return privKeyPath;
    }

    public void setPrivKeyPath(String privKeyPath) {
        this.privKeyPath = privKeyPath;
    }

    private String privKeyPath;
    private Map<String, String> files = new HashMap<>();

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public ServerConfig addEntry(String binaryFile, String versionFile) {
        files.put(binaryFile, versionFile);
        return this;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setCertName(String certName) {
        this.certName = certName;
    }

    public String getCertName() {
        return certName;
    }
}
