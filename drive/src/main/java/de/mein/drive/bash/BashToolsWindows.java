package de.mein.drive.bash;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.MeinService;
import org.jdeferred.Promise;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    @Override
    public void setBinPath(String binPath) {
        System.out.println("BashToolsWindows.setBinPath");
    }

    @Override
    public Set<Long> getINodesOfDirectory(AFile file) throws IOException {
        return null;
    }

    @Override
    public ModifiedAndInode getModifiedAndINodeOfFile(AFile file) throws IOException {
        //reads something like "File ID is 0x0000000000000000000200000000063a"
        String result = execLine("fsutil", "file", "queryfileid", file.getAbsolutePath());
        String id = result.substring(11);
        Long iNode = Long.decode(id);
        return new ModifiedAndInode(file.lastModified(), iNode);
    }

    @Override
    public void rmRf(AFile directory) throws IOException {
//        exec("rd /s /q \"" + directory.getAbsolutePath() + "\"");
        exec("rd", "/s", "/q", directory.getAbsolutePath());
    }

    @Override
    public List<String> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException {
        System.err.println("BashToolsWindows.stuffModifiedAfter.I AM THE WINDOWS GUY!");
        return null;
    }

    private String[] buildArgs(String... commands) {
        String[] result = new String[commands.length + 2];
        result[0] = BIN_PATH;
        result[1] = "/c";
        int i = 2;
        for (String command : commands) {
            result[i] = command;
            i++;
        }
        return result;
    }

    private Process exec(String... commands) throws IOException {
        //System.out.println("BashToolsWindows.exec: " + Arrays.toString(commands));
        String[] args = buildArgs(commands);
        return new ProcessBuilder(args).start();
    }

    private String execLine(String... commands) throws IOException {
        try {
            //todo debug
            Process process = exec(commands);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private WindowsBashReader execReader(String... commands) throws IOException {
        try {
            Process process = exec(commands);
            WindowsBashReader reader = new WindowsBashReader(new InputStreamReader(process.getInputStream(), CHARSET));
            return reader;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Iterator<String> find(AFile directory, AFile pruneDir) throws IOException {
        String cmd = "dir /b/s \"" + directory.getAbsolutePath()
                + "\" | findstr /v \"" + pruneDir.getAbsolutePath() + "\"";
        return execReader("dir", "/b/s", directory.getAbsolutePath(), "|", "findstr", "/vc:\"" + pruneDir.getAbsolutePath() + "\"").lines().iterator();
    }

    @Override
    public Promise<Long, Exception, Void> getInode(AFile f) {
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
        //process.waitFor();
        return reader.lines();
    }

    @Override
    public Iterator<String> stuffModifiedAfter(AFile directory, AFile pruneDir, long timeStamp) throws IOException, InterruptedException {
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

    @Override
    public void mkdir(AFile dir) throws IOException {
        exec("mkdir", dir.getAbsolutePath());
    }
}
