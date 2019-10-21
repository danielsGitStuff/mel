package de.mel.update;

import de.mel.Lok;
import de.mel.Versioner;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.text.ParseException;
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

    public void searchUpdate() throws UnrecoverableKeyException, IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
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

    void onVersionAvailable(VersionAnswerEntry versionEntry) {
        try {
            String currentCommit = Versioner.getVersion();
            String currentVersion = Versioner.getVersion();
            Lok.debug("current: commit=" + currentCommit + " timestamp=" + currentVersion);
            Lok.debug("latest : commit=" + versionEntry.getCommit() + " version=" + versionEntry.getVersion());
            if (Versioner.isYounger(currentVersion, versionEntry.getVersion())) {
                Lok.debug("no update necessary :)");
                N.forEachAdvIgnorantly(updateHandlers, (stoppable, index, updateHandler) -> updateHandler.onNoUpdateAvailable(this));
                return;
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            return;
        }
        N.forEachAdvIgnorantly(updateHandlers, (stoppable, index, updateHandler) -> updateHandler.onUpdateAvailable(this, versionEntry));
    }

    void onUpdateReceived(VersionAnswerEntry versionEntry, File target) {
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

    /**
     * Retrieve the binary file
     *
     * @param versionEntry
     * @param target
     */
    public void loadUpdate(VersionAnswerEntry versionEntry, File target) {
        for (String urlString : versionEntry.getMirrors()) {
            FileOutputStream fos = null;
            InputStream input = null;
            try {
                Lok.info("transferring update (src=" + urlString + ")");
                URL url = new URL(urlString);
                input = url.openStream();
                fos = new FileOutputStream(target);
                if (BinarySocket.transferToOutputStream(this, fos, input, versionEntry.getHash(), versionEntry.getLength())) {
                    Lok.info("update transferred successfully (src=" + urlString + ")");
                    return;
                } else {
                    Lok.error("could not transfer update (src=" + urlString + ")");
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            } finally {
                if (input != null)
                    N.r(input::close);
                if (fos != null)
                    N.r(fos::close);
            }
            loadFromXorserv(versionEntry, target);
        }
    }

    private void loadFromXorserv(VersionAnswerEntry versionEntry, File target) {
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
