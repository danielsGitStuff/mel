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
        String result = execLine("fsutil file queryfileid \"" + file.getAbsolutePath() + "\"");
        result = result.substring(11);
        return Long.decode(result);
    }

    @Override
    public void rmRf(File directory) throws IOException {
        exec("rd /s /q \"" + directory.getAbsolutePath() + "\"");
    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return null;
    }

    private Process exec(String command) throws IOException {
        System.out.println("BashToolsWindows.exec for: " + command);
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

    private WindowsCmdReader execReader(String command) throws IOException {
        try {
            Process process = exec(command);
            WindowsCmdReader reader = new WindowsCmdReader(new InputStreamReader(process.getInputStream()));
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
    public Stream<String> find(File directory, File pruneDir) throws IOException {
        String cmd = "dir /b/s \"" + directory.getAbsolutePath()
                + "\" | findstr /v \"" + pruneDir.getAbsolutePath() + "\"";
        return execReader(cmd).lines();
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }

    private Stream<String> execPowerShell(String command){
        try {
            Process process = exec("powershell.exe @'"+command+"'@");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.lines();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Stream<String> stuffModifiedAfter(File directory, long timeStamp) {
        Double winTimeStamp = timeStamp / 1000d;
        //get-childitem "C:\Users\thefa\IdeaProjects\jdkbug\testfolder\" -recurse | where {(Get-Date($_.LastWriteTime) -UFormat "%s") -gt 1500167894.53368} | foreach {$_.FullName}
        String command = "get-childitem \""+directory.getAbsolutePath()+"\" -recurse | where {(Get-Date($_.LastWriteTime) -UFormat \"%s\") -gt "+winTimeStamp+"} | foreach {$_.FullName}";
        return execPowerShell(command);
    }
}
