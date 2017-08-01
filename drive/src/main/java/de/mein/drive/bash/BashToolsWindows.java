package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.*;
import java.util.Iterator;
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
    public ModifiedAndInode getModifiedAndINodeOfFile(File file) throws IOException {
        String result = execLine("fsutil file queryfileid \"" + file.getAbsolutePath() + "\"");
        result = result.substring(11);
        Long iNode = Long.decode(result);
        return new ModifiedAndInode(file.lastModified(), iNode);
    }

    @Override
    public void rmRf(File directory) throws IOException {
        exec("rd /s /q \"" + directory.getAbsolutePath() + "\"");
    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        System.err.println("BashToolsWindows.stuffModifiedAfter.I AM THE WINDOWS GUY!");
        return null;
    }

    private Process exec(String command) throws IOException {
        System.out.println("BashToolsWindows.exec: " + command);
        String[] args = new String[]{BIN_PATH};
        Process process = new ProcessBuilder(args).start();
        PrintWriter stdin = new PrintWriter(process.getOutputStream());
        stdin.println(command);
        stdin.close();
        return process;
    }

    private String execLine(String command) throws IOException {
        try {
            Process process = exec(command);
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

    private WindowsBashReader execReader(String command) throws IOException {
        try {
            Process process = exec(command);
            WindowsBashReader reader = new WindowsBashReader(new InputStreamReader(process.getInputStream()));
            String s = "--nix--";
            s = reader.readLine();
            s = reader.readLine();
            s = reader.readLine();
            s = reader.readLine();
            return reader;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Iterator<String> find(File directory, File pruneDir) throws IOException {
        String cmd = "dir /b/s \"" + directory.getAbsolutePath()
                + "\" | findstr /v \"" + pruneDir.getAbsolutePath() + "\"";
        return execReader(cmd).lines().iterator();
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }

    private Stream<String> execPowerShell(String command, String prependLine) throws IOException, InterruptedException {
        System.out.println("BashToolsWindows.execPowerShell: " + command);
        String[] args = new String[]{"powershell.exe"};
        Process process = new ProcessBuilder(args).start();
        PrintWriter stdin = new PrintWriter(process.getOutputStream());
        stdin.println(command);
        stdin.close();
        WindowsPowerReader reader = new WindowsPowerReader(new InputStreamReader(process.getInputStream()));
        reader.prependLine(prependLine);
        process.waitFor();
        return reader.lines();
    }

    @Override
    public Iterator<String> stuffModifiedAfter(File directory, File pruneDir, long timeStamp) throws IOException, InterruptedException {
        Double winTimeStamp = timeStamp / 1000d;
        String prependLine = null;
        Object lm = directory.lastModified();
        if (directory.lastModified() >= timeStamp) {
            prependLine = directory.getAbsolutePath();
        }
        String command = "get-childitem \"" + directory.getAbsolutePath() + "\" -recurse | " +
                "where {(Get-Date($_.LastWriteTime.ToUniversalTime()) -UFormat \"%s\") -gt " + winTimeStamp + " -and -not $_.FullName.StartsWith(\"" + pruneDir.getAbsolutePath() + "\")} " +
                "| foreach {$_.FullName}";
        return execPowerShell(command, prependLine).iterator();
    }
}
