package de.mein.drive.bash;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.auth.file.DefaultFileConfiguration;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by xor on 13.07.2017.
 */
public class BashToolsUnix implements BashToolsImpl {

    protected String BIN_PATH = "/bin/sh";
    private ExecutorService executorService = Executors.newCachedThreadPool();


    public static void main(String[] args) throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        AFile f = AFile.instance("f");
        f.mkdirs();
        BashToolsUnix bashToolsUnix = new BashToolsUnix();
        ModifiedAndInode modifiedAndInode = bashToolsUnix.getModifiedAndInode(f);
        Lok.debug("mod " + modifiedAndInode.getModified() + " ... inode " + modifiedAndInode.getiNode());
        f.delete();
    }


    public ModifiedAndInode getModifiedAndInode(AFile file) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "stat -c %Y\" \"%i " + escapeAbsoluteFilePath(file)};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line = reader.readLine();
        String[] split = line.split(" ");
        return new ModifiedAndInode(Long.parseLong(split[0]), Long.parseLong(split[1]));
    }


    @Override
    public void setBinPath(String binPath) {
        BIN_PATH = binPath;
    }

    @Override
    public Set<Long> getINodesOfDirectory(AFile file) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "find share/ -printf \"%p\\n\" | tail -n +2 | xargs -d '\\n' stat -c %i"};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        Set<Long> iNodes = new HashSet<>();
        String line = "";
        while ((line = reader.readLine()) != null) {
            iNodes.add(Long.parseLong(line));
        }
        return iNodes;
    }

    /**
     * escapes this character: "
     *
     * @param file
     * @return
     */
    protected String escapeAbsoluteFilePath(AFile file) {
        return "\"" + file.getAbsolutePath()
                .replaceAll("\"", "\\\\\"")
                .replaceAll("`", "\\\\`")
                .replaceAll("\\$", "\\\\\\$")
                + "\"";
    }

    @Override
    public ModifiedAndInode getModifiedAndINodeOfFile(AFile file) throws IOException, InterruptedException {
        String[] args = new String[]{BIN_PATH, "-c", "stat -c %i\\ %Y " + escapeAbsoluteFilePath(file)};
        Process proc = new ProcessBuilder(args).start();
        //proc.waitFor(); // this line sometimes hangs. Process.exitcode is 0 and Process.hasExited is false
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line = reader.readLine();
        //todo debug
        if (line == null) {
            Lok.debug("reading error for: " + args[2]);
            try {
                BufferedReader r = new BufferedReader((new InputStreamReader(proc.getErrorStream())));
                String l = r.readLine();
                while (l != null) {
                    Lok.debug(l);
                    l = r.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String[] parts = line.split(" ");
        Long iNode = Long.parseLong(parts[0]);
        Long modified = Long.parseLong(parts[1]);
        return new ModifiedAndInode(modified, iNode);
    }

    /**
     * rm -rf
     *
     * @param directory
     */
    @Override
    public void rmRf(AFile directory) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "rm -rf " + escapeAbsoluteFilePath(directory)};
        Process proc = new ProcessBuilder(args).start();
        N.oneLine(proc::waitFor);
    }

    @Override
    public List<AFile> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException {
        Lok.debug("BashTools.stuffModifiedAfter: " + referenceFile.getName() + " mod: " + referenceFile.lastModified());
//        String cmd = "find \"" + directory.getAbsolutePath() + "\"  "
//                + " -path \"" + pruneDir + "\" -prune"
//                + " -o -newer \"" + referenceFile.getAbsolutePath() + "\" -print";
        String cmd = "find " + escapeAbsoluteFilePath(directory)
                + " -path " + escapeAbsoluteFilePath(pruneDir) + " -prune"
                + " -o -newer " + escapeAbsoluteFilePath(referenceFile) + " -print";
        Lok.debug("BashTools.stuffModifiedAfter.cmd: " + cmd);
        String[] args = new String[]{BIN_PATH, "-c",
                cmd};
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        Process proc = processBuilder.start();
        Lok.debug("BashTools.stuffModifiedAfter.collecting.result");
        List<AFile> result = new ArrayList<>();
        Iterator<AFile> iterator = BashTools.inputStreamToFileIterator(proc.getInputStream());
        while (iterator.hasNext()) {
            AFile path = iterator.next();
            Lok.debug(getClass().getSimpleName() + ".stuffModifiedAfter.collected: " + path);
            result.add(path);
        }
        Lok.debug("BashTools.stuffModifiedAfter.collecting.done");
        return result;
    }

    private Iterator<AFile> exec(String cmd) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c",
                cmd};
        Lok.debug("BashToolsUnix.exec: " + cmd);
        Process proc = new ProcessBuilder(args).start();
        return BashTools.inputStreamToFileIterator(proc.getInputStream());
    }

    @Override
    public Iterator<AFile> find(AFile directory, AFile pruneDir) throws IOException {
        return exec("find " + escapeAbsoluteFilePath(directory) + " -mindepth 1" + " -path " + escapeAbsoluteFilePath(pruneDir) + " -prune -o -print");
    }

    @Override
    public Promise<Long, Exception, Void> getInode(AFile f) {
        DeferredObject<Long, Exception, Void> deferred = new DeferredObject<>();
        executorService.execute(() -> N.r(() -> {
            String ba = "ls -i -d " + escapeAbsoluteFilePath(f);
            String[] args = new String[]{BIN_PATH, "-c", ba};
            Long inode;
            List<String> lines;
            boolean hasFinished = false;
            Process proc = null;
            while (!hasFinished) {
                try {
                    proc = new ProcessBuilder(args).start();
                    hasFinished = proc.waitFor(10, TimeUnit.SECONDS);
                    if (!hasFinished) {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                        List<String> errors = errorReader.lines().collect(Collectors.toList());
                        Lok.debug("BashTools.stuffModifiedAfter.did not finish");
                    }
                    // try to read anyway.
                    // the process might have come to an end but Process.waitFor() does not always finish.
                    Lok.debug("BashTools.stuffModifiedAfter");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    Lok.debug("BashTools.stuffModifiedAfter.collecting.result");
                    lines = reader.lines().collect(Collectors.toList());
                    lines.forEach(s -> Lok.debug("BashTools.getNodeAndTime.LLLL " + s));
                    String[] s = lines.get(0).split(" ");
                    if (s[0].isEmpty())
                        Lok.debug("BashTools.getNodeAndTime");
                    inode = Long.parseLong(s[0]);
                    deferred.resolve(inode);
                    hasFinished = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    proc.destroyForcibly();
                    continue;
                }
            }
        }));
        return deferred;
    }

    @Override
    public Iterator<AFile> stuffModifiedAfter(AFile directory, AFile pruneDir, long timeStamp) {
        System.err.println("BashToolsUnix.stuffModifiedAfter()... I AM THE UNIX GUY! >:(");
        return null;
    }

    @Override
    public void mkdir(AFile dir) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c",
                "mkdir " + escapeAbsoluteFilePath(dir)};
        new ProcessBuilder(args).start();
    }

    @Override
    public boolean mv(File source, File target) throws IOException {
        String src = source.getAbsolutePath().replaceAll("'", "\\'");
        String tgt = target.getAbsolutePath().replaceAll("'", "\\'");
        String cmd = "mv '" + src + "' '" + tgt + "'";
        String[] args = new String[]{BIN_PATH, "-c", cmd};
        Process process = new ProcessBuilder(args).start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String error = reader.readLine();
        if (error == null)
            return true;
        Lok.debug("BashToolsUnix.mv");
        return false;
    }


    public Long getInotifyLimit() throws IOException {
        // "cat /proc/sys/fs/inotify/max_user_watches";
        File f = new File("/proc/sys/fs/inotify/max_user_watches");
        List<String> lines = Files.readAllLines(Paths.get(f.toURI()));
        return Long.parseLong(lines.get(0));
//        String[] args = new String[]{BIN_PATH, "-c", "cat " + "/proc/sys/fs/inotify/max_user_watches"};
//        Process proc = new ProcessBuilder(args).start();
//        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
//        String line = reader.readLine();
////        String[] split = line.split(" ");
//        Long limit = Long.parseLong(line);
//        return limit;
    }

    public Long countSubDirs(File dir) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "find " + escapeAbsoluteFilePath(AFile.instance(dir)) + "  -mindepth 1  -type d | wc -l"};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line = reader.readLine();
//        String[] split = line.split(" ");
        Long subDirCount = Long.parseLong(line);
        return subDirCount;
    }
}
