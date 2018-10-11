package de.miniserver.socket;

import de.mein.Lok;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.tools.N;
import de.miniserver.data.FileRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * delivers a binary file
 */
public class BinarySocket extends SimpleSocket {

    public static final String QUERY_FILE = "f=";
    private final FileRepository fileRepository;

    public BinarySocket(Socket socket, FileRepository fileRepository) throws IOException {
        super(socket);
        this.fileRepository = fileRepository;
    }

    @Override
    public void run() {
        FileInputStream fin = null;
        try {
            String s = in.readUTF();
            if (s.startsWith(QUERY_FILE)) {
                String hash = s.substring(QUERY_FILE.length(), s.length());
                File f = fileRepository.getFile(hash);
                Lok.debug("reading file: "+f.getAbsolutePath());
                fin = new FileInputStream(f);
                byte[] bytes = new byte[MeinSocket.BLOCK_SIZE];
                int read;
                do {
                    read = fin.read(bytes);
                    if (read > 0) {
                        Lok.debug("sending block");
                        out.write(bytes, 0, read);
                    }
                } while (read > 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try{
                fin.close();
            }catch (Exception e){}
            shutdown();
        }
    }


}
