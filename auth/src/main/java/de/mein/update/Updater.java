package de.mein.update;

import de.mein.Lok;
import de.mein.Versioner;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.HashSet;
import java.util.Set;

public class Updater {
    private final MeinAuthService meinAuthService;
    private final CertificateManager cm;
    private final MeinAuthSettings settings;
    private final File target;
    private UpdateMessageSocket updateMessageSocket;
    private BinarySocket binarySocket;
    private Set<UpdateHandler> updateHandlers = new HashSet<>();

    public Updater(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        this.cm = meinAuthService.getCertificateManager();
        this.settings = meinAuthService.getSettings();
        this.target = new File(settings.getWorkingDirectory(), settings.getVariant());
    }

    public void retrieveUpdate() throws UnrecoverableKeyException, IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (updateMessageSocket != null)
            updateMessageSocket.onShutDown();
        if (binarySocket != null)
            binarySocket.onShutDown();
        target.delete();
        Socket socket = cm.createSocket();
        String url = settings.getUpdateUrl();
        String variant = Versioner.getBuildVariant();
        int port = settings.getUpdateMessagePort();
        socket.connect(new InetSocketAddress(url, port));
        updateMessageSocket = new UpdateMessageSocket(this, socket, variant, meinAuthService.getCertificateManager().getUpdateServerCertificateHash());
        meinAuthService.execute(updateMessageSocket);
    }

    void onVersionAvailable(VersionAnswer.VersionEntry versionEntry) {
        try {
            Long currentVersion = Versioner.getBuildVersion();
            Lok.debug("current version: " + currentVersion);
            Lok.debug("latest version : " + versionEntry.getVersion());
            if (currentVersion >= versionEntry.getVersion()) {
                Lok.debug("no update necessary :)");
                N.forEachAdvIgnorantly(updateHandlers, (stoppable, index, updateHandler) -> updateHandler.onNoUpdateAvailable(this));
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        N.forEachAdvIgnorantly(updateHandlers, (stoppable, index, updateHandler) -> updateHandler.onUpdateAvailable(this, versionEntry));
    }

    void onUpdateReceived(VersionAnswer.VersionEntry versionEntry, File target) {
        Lok.debug("Success. I got Update!!!1!");
        N.forEachAdvIgnorantly(updateHandlers, (stoppable, index, updateHandler) -> updateHandler.onUpdateFileReceived(this, versionEntry, target));
    }

    public Updater addUpdateHandler(UpdateHandler updateHandler) {
        updateHandlers.add(updateHandler);
        return this;
    }

    public Updater removeUpdateHandler(UpdateHandler updateHandler) {
        updateHandlers.remove(updateHandler);
        return this;
    }

    void onSocketProgress(Long done, Long length) {
        N.forEachAdvIgnorantly(updateHandlers, (stoppable, index, updateHandler) -> updateHandler.onProgress(this, done, length));
    }

    public void loadUpdate(VersionAnswer.VersionEntry versionEntry, File target) {
        String url = settings.getUpdateUrl();
        int port = settings.getUpdateBinaryPort();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(url, port));
            binarySocket = new BinarySocket(this, socket, versionEntry, target);
            meinAuthService.execute(binarySocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Updater clearUpdateHandlers() {
        updateHandlers.clear();
        return this;
    }
}
