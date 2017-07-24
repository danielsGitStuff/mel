package de.mein.android.drive.bash;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsException;
import de.mein.drive.bash.BashToolsJava;
import de.mein.drive.bash.BashToolsUnix;

/**
 * Created by xor on 7/20/17.
 */

public class BashToolsAndroid extends BashToolsUnix {
    private final Context context;
    private BashToolsJava javaFallback;
    private BashToolsJava findFallBack;

    public BashToolsAndroid(Context context) {
        this.context = context;
        BIN_PATH = "/system/bin/sh";
        javaFallback = new BashToolsJava();
        testCommands();
    }

    private void testCommands() {
        // find
        String cmd = "";
        try {
            File cacheDir = context.getCacheDir();
            File dir = new File(cacheDir + File.separator + "bash.test");
            File prune = new File(dir.getAbsolutePath() + File.separator + "prune");
            File file = new File(dir.getAbsolutePath() + File.separator + "file");
            dir.mkdirs();
            prune.mkdirs();
            file.createNewFile();
            cmd = "find \"" + dir.getAbsolutePath() + "\" -mindepth 1" + " -path \"" + prune + "\" -prune -o -print";
            Iterator<String> iterator = testCommand(cmd);
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.equals(dir.getAbsolutePath()))
                    throw new BashToolsException("'find' ignored '-mindepth 1");
                if (line.equals(prune.getAbsolutePath())) {
                    throw new BashToolsException("'find' ignored '-prune");
                }
            }
        } catch (Exception e) {
            System.err.println(getClass().getSimpleName() + ".did not work as expected: " + cmd);
            System.err.println(getClass().getSimpleName()+".using.fallback.for 'find'");
            findFallBack = javaFallback;
            e.printStackTrace();
        }
    }

    private Iterator<String> testCommand(String cmd) throws IOException, InterruptedException {
        String[] args = new String[]{BIN_PATH, "-c",
                cmd};
        System.out.println("BashToolsUnix.exec: " + cmd);
        Process proc = new ProcessBuilder(args).start();
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new BashToolsException(proc);
        }
        System.out.println("BashTest.exec." + proc.exitValue());
        return BashTools.inputStreamToIterator(proc.getInputStream());
    }

    @Override
    public Iterator<String> find(File directory, File pruneDir) throws IOException {
        if (findFallBack!=null)
            return findFallBack.find(directory,pruneDir);
        return super.find(directory, pruneDir);
    }
}
