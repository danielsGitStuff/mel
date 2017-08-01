package de.mein.android.drive.bash;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsException;
import de.mein.drive.bash.BashToolsJava;
import de.mein.drive.bash.BashToolsUnix;

/**
 * Created by xor on 7/20/17.
 */

public class BashToolsAndroid extends BashToolsUnix {
    private final Context context;
    private BashToolsJava javaBashTools;
    private BashToolsJava findFallBack;


    public BashToolsAndroid(Context context) {
        this.context = context;
        BIN_PATH = "/system/bin/sh";
        javaBashTools = new BashToolsJava();
        testCommands();
    }

    private void testCommands() {
        // find
        String cmd = "";
        File cacheDir = context.getCacheDir();
        File dir = new File(cacheDir + File.separator + "bash.test");
        File prune = new File(dir.getAbsolutePath() + File.separator + "prune");
        File file = new File(dir.getAbsolutePath() + File.separator + "file");
        try {
            dir.mkdirs();
            prune.mkdirs();
            file.createNewFile();
            cmd = "find \"" + dir.getAbsolutePath() + "\" -path \"" + prune + "\" -prune -o -print";
            Streams streams = testCommand(cmd);
            Iterator<String> iterator = streams.stdout;
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.equals(prune.getAbsolutePath())) {
                    throw new BashToolsException("'find' ignored '-prune");
                }
            }
            while (streams.stderr.hasNext())
                System.err.println(getClass().getSimpleName() + ".testCommands(): " + streams.stderr.next());
        } catch (Exception e) {
            System.err.println(getClass().getSimpleName() + ".did not work as expected: " + cmd);
            System.err.println(getClass().getSimpleName() + ".using.fallback.for 'find'");
            findFallBack = javaBashTools;
            e.printStackTrace();
        }
        // ls -i
        try {
            String regex = "^\\ +\\d+\\ images";
        }catch (Exception e){

        }
    }

    class Streams {
        Iterator<String> stdout;
        Iterator<String> stderr;
    }

    private Streams testCommand(String cmd) throws IOException, InterruptedException {
        String[] args = new String[]{BIN_PATH, "-c",
                cmd};
        System.out.println("BashToolsUnix.exec: " + cmd);
        Process proc = new ProcessBuilder(args).start();
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new BashToolsException(proc);
        }
        System.out.println("BashTest.exec." + proc.exitValue());
        Streams streams = new Streams();
        streams.stdout = BashTools.inputStreamToIterator(proc.getInputStream());
        streams.stderr = BashTools.inputStreamToIterator(proc.getErrorStream());
        return streams;
    }

    @Override
    public Long getINodeOfFile(File file) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "ls -i \"" + file.getAbsolutePath() + "\""};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        String line = reader.readLine();
        if (line==null)
            System.err.println("erjng0w54");
        line = line.trim();
        String node = line.split(" ")[0];
        return Long.parseLong(node);
    }

    @Override
    public Iterator<String> find(File directory, File pruneDir) throws IOException {
        if (findFallBack != null)
            return findFallBack.find(directory, pruneDir);
        return super.find(directory, pruneDir);
    }
}
