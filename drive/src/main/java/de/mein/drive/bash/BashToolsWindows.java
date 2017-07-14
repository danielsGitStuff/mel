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
        String result = execLine("fsutil file queryfileid \"" + file.getAbsolutePath()+"\"");
        result = result.substring(11);
        return Long.decode(result);
    }

    @Override
    public void rmRf(File directory) throws IOException {
        System.out.println("BashToolsWindows.rmRf");
    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return null;
    }

    private Process createProcess(String command) throws IOException, InterruptedException {
        System.out.println("BashToolsWindows.createProcess for: " + command);
        String[] args = new String[]{BIN_PATH};
        Process process = new ProcessBuilder(args).start();
        PrintWriter stdin = new PrintWriter(process.getOutputStream());
        stdin.println(command);
        stdin.close();
        process.waitFor();
        return process;
    }

    private String execLine(String command) throws IOException {
        try {
            Process process = createProcess(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.readLine();
            reader.readLine();
            reader.readLine();
            reader.readLine();
            String result = reader.readLine();
            reader.lines();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private WindowsCmdReader execReader(String command) throws IOException {
        try {
            Process process = createProcess(command);
            WindowsCmdReader reader = new WindowsCmdReader(new InputStreamReader(process.getInputStream()));
            String s = "--nix--";
            s=reader.readLine();
            s=reader.readLine();
            s=reader.readLine();
            s=reader.readLine();
            return reader;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Stream<String> find(File directory, File pruneDir) throws IOException {
        String cmd = "dir /b/s \"" + directory.getAbsolutePath()
                + "\" | findstr /v \"" + pruneDir.getAbsolutePath() + "\"";
        return execReader(cmd).lines();
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }
}
