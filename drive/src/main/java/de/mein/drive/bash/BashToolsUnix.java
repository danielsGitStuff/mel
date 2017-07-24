package de.mein.drive.bash;

import de.mein.auth.tools.N;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by xor on 13.07.2017.
 */
public class BashToolsUnix implements BashToolsImpl {

    protected String BIN_PATH = "/bin/bash";
    private ExecutorService executorService = Executors.newCachedThreadPool();


    @Override
    public void setBinPath(String binPath) {
        BIN_PATH = binPath;
    }

    @Override
    public Set<Long> getINodesOfDirectory(File file) throws IOException {
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

    @Override
    public Long getINodeOfFile(File file) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "stat -c %i \"" + file.getAbsolutePath() + "\""};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String node = reader.readLine();
        return Long.parseLong(node);
    }

    /**
     * rm -rf
     *
     * @param directory
     */
    @Override
    public void rmRf(File directory) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "rm -rf \"" + directory.getAbsolutePath() + "\""};
        Process proc = new ProcessBuilder(args).start();
    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        System.out.println("BashTools.stuffModifiedAfter: " + referenceFile.getName() + " mod: " + referenceFile.lastModified());
        String[] args = new String[]{BIN_PATH, "-c",
                "find \"" + directory.getAbsolutePath() + "\" -mindepth 1"
                        + " -path \"" + pruneDir + "\" -prune"
                        + " -o -newer \"" + referenceFile.getAbsolutePath() + "\" -print"};
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        Process proc = processBuilder.start();
        boolean hasFinished = false;
        while (!hasFinished) {
            try {
                hasFinished = proc.waitFor(10, TimeUnit.SECONDS);
                if (!hasFinished) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                    List<String> errors = errorReader.lines().collect(Collectors.toList());
                    System.out.println("BashTools.stuffModifiedAfter.did not finish");
                    for (String s : errors)
                        System.out.println("BashTools.stuffModifiedAfter.ERROR: " + s);
                }
                int exitValue = proc.exitValue();
                if (exitValue == 0) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    System.out.println("BashTools.stuffModifiedAfter.collecting.result");
                    List<String> result = reader.lines().collect(Collectors.toList());
                    System.out.println("BashTools.stuffModifiedAfter.collecting.done");
                    return result;
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                    throw new BashToolsException(reader.lines());
                }
            } catch (Exception e) {
                e.printStackTrace();
                proc.destroyForcibly();
                continue;
            }
        }
        return null;
    }

    private Iterator<String> exec(String cmd) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c",
                cmd};
        System.out.println("BashToolsUnix.exec: " + cmd);
        Process proc = new ProcessBuilder(args).start();
        return readerToIterator(proc.getInputStream());
    }

    protected Iterator<String> readerToIterator(InputStream inputStream){
        BufferedIterator bufferedReader = new BufferedIterator(new InputStreamReader(inputStream));
        return bufferedReader.iterator();
    }

    @Override
    public Iterator<String> find(File directory, File pruneDir) throws IOException {
        return exec("find \"" + directory.getAbsolutePath() + "\" -mindepth 1" + " -path \"" + pruneDir + "\" -prune -o -print");
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        DeferredObject<Long, Exception, Void> deferred = new DeferredObject<>();
        executorService.execute(() -> N.r(() -> {
            String ba = "ls -i -d '" + f.getAbsolutePath() + "'";
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
                        System.out.println("BashTools.stuffModifiedAfter.did not finish");
                    }
                    // try to read anyway.
                    // the process might have come to an end but Process.waitFor() does not always finish.
                    System.out.println("BashTools.stuffModifiedAfter");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    System.out.println("BashTools.stuffModifiedAfter.collecting.result");
                    lines = reader.lines().collect(Collectors.toList());
                    lines.forEach(s -> System.out.println("BashTools.getNodeAndTime.LLLL " + s));
                    String[] s = lines.get(0).split(" ");
                    if (s[0].isEmpty())
                        System.out.println("BashTools.getNodeAndTime");
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
    public Iterator<String> stuffModifiedAfter(File originalFile, File pruneDir, long timeStamp) {
        System.err.println("BashToolsUnix.stuffModifiedAfter()... I AM THE UNIX GUY! >:(");
        return null;
    }
}
