package de.mel.update;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.socket.MelSocket;
import de.mel.sql.Hash;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BinarySocket extends SimpleSocket {
    private final VersionAnswerEntry versionEntry;
    private final File target;
    private final Updater updater;

    public BinarySocket(Updater updater, Socket socket, VersionAnswerEntry versionEntry, File target) throws IOException {
        super(socket);
        this.versionEntry = versionEntry;
        this.target = target;
        this.updater = updater;
    }

    @Override
    public void runImpl() {
        String question = MelStrings.update.QUERY_FILE + versionEntry.getHash();
        try {
            FileOutputStream fos = new FileOutputStream(target);
            MessageDigest messageDigest = Hash.createSHA256MessageDigest();
            // ask the server for file
            out.writeUTF(question);
            // store answer
            if (transferToOutputStream(updater, fos, socket.getInputStream(), versionEntry.getHash(), versionEntry.getLength()))
                updater.onUpdateReceived(versionEntry, target);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public static boolean transferToOutputStream(Updater updater, FileOutputStream fos, InputStream input, String expectedHash, Long expecedSize) throws IOException, NoSuchAlgorithmException {
        MessageDigest messageDigest = Hash.createSHA256MessageDigest();
        byte[] bytes;
        bytes = new byte[MelSocket.BLOCK_SIZE];
        int read;
        Long sum = 0L;
        updater.onSocketProgress(sum, expecedSize);
        do {
            read = input.read(bytes);
            if (read > 0) {
                fos.write(bytes, 0, read);
                messageDigest.update(bytes, 0, read);
                sum += read;
                updater.onSocketProgress(sum, expecedSize);
            }
        } while (read > 0);
        String receivedHash = Hash.bytesToString(messageDigest.digest());
        if (receivedHash.equals(expectedHash))
            return true;
        else {
            Lok.error("expected hash: " + expectedHash + ", received: " + receivedHash);
            return false;
        }
    }
}
