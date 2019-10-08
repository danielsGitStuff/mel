package de.mel.update;

import de.mel.Lok;
import de.mel.Versioner;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;

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
    private final MelAuthService melAuthService;
    private final CertificateManager cm;
    private final MelAuthSettings settings;
    private final File target;
    private UpdateMessageSocket updateMessageSocket;
    private BinarySocket binarySocket;
    private Set<UpdateHandler> updateHandlers = new HashSet<>();

    public Updater(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
        this.cm = melAuthService.getCertificateManager();
        this.settings = melAuthService.getSettings();
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
        updateMessageSocket = new UpdateMessageSocket(this, socket, variant, melAuthService.getCertificateManager().getUpdateServerCertificateHash());
        melAuthService.execute(updateMessageSocket);
    }

    void onVersionAvailable(VersionAnswer.VersionEntry versionEntry) {
        try {
            String currentCommit = Versioner.getCommit();
            Long timestamp = Versioner.getTimestamp();
            Lok.debug("current: commit=" + currentCommit + " timestamp=" + timestamp);
            Lok.debug("latest : commit=" + versionEntry.getCommit() + " timestamp=" + versionEntry.getTimestamp());
            if (timestamp >= versionEntry.getTimestamp()) {
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
            melAuthService.execute(binarySocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Updater clearUpdateHandlers() {
        updateHandlers.clear();
        return this;
    }
}
