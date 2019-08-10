package de.mein.android.drive.bash;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.drive.bash.*;
import de.mein.drive.bash.BashToolsAndroidJavaImpl;

/**
 * Created by xor on 7/20/17.
 */

public class BashToolsAndroid extends BashToolsUnix {
    private final Context context;
    private BashToolsAndroidJavaImpl javaBashTools;
    private BashToolsAndroidJavaImpl findFallBack;


    public BashToolsAndroid(Context context) {
        this.context = context;
        setBIN_PATH("/system/bin/sh");
        javaBashTools = new BashToolsAndroidJavaImpl();
        testCommands();
    }


    private void testCommands() {
        // find
        // in case find fails and we are on android 5+ we can use the storage access framework instead of the bash.
        // but in case it works we will stick to that
        String cmd = "";
        AFile cacheDir = AFile.instance(context.getCacheDir());
        AFile dir = AFile.instance(cacheDir, "bash.test");
        AFile prune = AFile.instance(dir, "prune");
        AFile file = AFile.instance(dir, "file");
        try {
            dir.mkdirs();
            prune.mkdirs();
            file.createNewFile();
            cmd = "find \"" + dir.getAbsolutePath() + "\" -path " + escapeQuotedAbsoluteFilePath(prune) + " -prune -o -print";
            Streams streams = testCommand(cmd);
            Iterator<AFile> iterator = streams.stdout;
            while (iterator.hasNext()) {
                Lok.error("no SAF");
                AFile line = iterator.next();
                if (line.equals(prune.getAbsolutePath())) {
                    throw new BashToolsException("'find' ignored '-prune");
                }
            }
            while (streams.stderr.hasNext())
                Lok.error("testCommands(): " + streams.stderr.next());
            cmd = "find \"" + cacheDir.getAbsolutePath() + "\" -newercc " + escapeAbsoluteFilePath(dir) + " -o -print";
            streams = testCommand(cmd);
            iterator = streams.stdout;
            while (iterator.hasNext()) {
                AFile line = iterator.next();
                if (line.equals(""))
                    Lok.debug();
            }
        } catch (Exception e) {
            Lok.error("did not work as expected: " + cmd);
            Lok.error("using.fallback.for 'find'");
            findFallBack = javaBashTools;
            e.printStackTrace();
        }
    }

    class Streams {
        Iterator<AFile> stdout;
        Iterator<String> stderr;
    }

    private Streams testCommand(String cmd) throws IOException, InterruptedException {
        String[] args = new String[]{getBIN_PATH(), "-c",
                cmd};
        Lok.debug("BashToolsUnix.exec: " + cmd);
        Process proc = new ProcessBuilder(args).start();
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new BashToolsException(proc);
        }
        Lok.debug("BashTest.exec." + proc.exitValue());
        Streams streams = new Streams();
        streams.stdout = BashTools.inputStreamToFileIterator(proc.getInputStream());
        streams.stderr = BashTools.inputStreamToIterator(proc.getErrorStream());
        return streams;
    }

    @Override
    public FsBashDetails getFsBashDetails(AFile file) throws IOException {
        final String cmd = "ls -id " + escapeQuotedAbsoluteFilePath(file);
        String[] args = new String[]{getBIN_PATH(), "-c", cmd};
        String line;
        try {
            Process proc = new ProcessBuilder(args).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            line = reader.readLine();
            line = line.trim();
            String[] parts = line.split(" ");
            Long iNode = Long.parseLong(parts[0]);
            /**
             * Testing revealed that Android P (maybe even earlier) does not support creating symlinks via terminal.
             * Thus supporting symlinks on Android is pointless.
             */
            return new FsBashDetails(file.lastModified(), iNode, false, null, file.getName());
        } catch (Exception e) {
            Lok.error("could not execute:\n " + cmd);
            throw new IOException(e);
        }
    }

    @Override
    public Iterator<AFile<?>> find(AFile directory, AFile pruneDir) throws IOException {
        if (findFallBack != null)
            return findFallBack.find(directory, pruneDir);
        return super.find(directory, pruneDir);
    }
}
