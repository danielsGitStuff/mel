package de.mein.update;

import de.mein.Lok;
import de.mein.Versioner;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.F;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

public class Updater {
    private final MeinAuthService meinAuthService;
    private final CertificateManager cm;
    private final MeinAuthSettings settings;
    private final File target;
    private UpdateMessageSocket updateMessageSocket;
    private BinarySocket binarySocket;

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
        int port = settings.getUpdateMessagePort();
        socket.connect(new InetSocketAddress(url, port));
        updateMessageSocket = new UpdateMessageSocket(this, socket, settings.getVariant());
        meinAuthService.execute(updateMessageSocket);
    }

    public void onVersionAvailable(VersionAnswer.VersionEntry versionEntry) {
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

    public void onUpdateReceived(VersionAnswer.VersionEntry versionEntry, File target) {
        Lok.debug("Success. I got Update!!!1!");
    }
}
