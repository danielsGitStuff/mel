package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by xor on 13.07.2017.
 */
@SuppressWarnings("Duplicates")
public class BashToolsWindows implements BashToolsImpl {
    private static final String BIN_PATH = "cmd";

    @Override
    public void setBinPath(String binPath) {
        System.out.println("BashToolsWindows.setBinPath");
    }

    @Override
    public Set<Long> getINodesOfDirectory(File file) throws IOException {
        return null;
    }

    @Override
    public Long getINodeOfFile(File file) throws IOException {
        return null;
    }

    @Override
    public void rmRf(File directory) throws IOException {
        System.out.println("BashToolsWindows.rmRf");
    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return null;
    }

    public WindowsCmdReader exec(String command) throws IOException {
        System.out.println("BashToolsWindows.exec: " + command);
        String[] args = new String[]{BIN_PATH};
        Process process = new ProcessBuilder(args).start();
        PrintWriter stdin = new PrintWriter(process.getOutputStream());
        stdin.println(command);
        stdin.close();
        WindowsCmdReader reader = new WindowsCmdReader(new InputStreamReader(process.getInputStream()));
        reader.readLine();
        reader.readLine();
        reader.readLine();
        reader.readLine();
        reader.readLine();
        return reader;
    }

    @Override
    public Stream<String> find(File directory, File pruneDir) throws IOException {
        String cmd = "dir /b/s \"" + directory.getAbsolutePath()
                + "\" | findstr /v \"" + pruneDir.getAbsolutePath() + "\"";
        BufferedReader reader = exec(cmd);
        reader.lines().forEach(path -> System.out.println("BashToolsWindows.found: " + path));
        return exec(cmd).lines();

    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }
}
