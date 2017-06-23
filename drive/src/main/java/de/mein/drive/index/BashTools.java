package de.mein.drive.index;

import de.mein.auth.tools.N;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by xor on 10/28/16.
 */
@SuppressWarnings("Duplicates")
public class BashTools {

    private static String BIN_PATH = "/bin/bash";

    public static void setBinPath(String binPath) {
        BIN_PATH = binPath;
    }

    public static Set<Long> getINodesOfDirectory(File file) throws IOException {
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

    public static Long getINodeOfFile(File file) throws IOException {
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
    public static void rmRf(File directory) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "rm -rf \"" + directory.getAbsolutePath() + "\""};
        Process proc = new ProcessBuilder(args).start();
    }


    public static List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        System.out.println("BashTools.stuffModifiedAfter.referenceFile: " + referenceFile.getAbsolutePath());
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
                }
                int exitValue = proc.exitValue();
                if (exitValue == 0) {
                    System.out.println("BashTools.stuffModifiedAfter");
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

    public static Stream<String> find(File directory, File pruneDir) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c",
                "find \"" + directory.getAbsolutePath() + "\" -mindepth 1"
                        + " -path \"" + pruneDir + "\" -prune -o -print"};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String res = null;
        try {
            proc.waitFor();
            return reader.lines();
        } catch (InterruptedException e) {
            System.err.println("string I got from bash: " + res);
            e.printStackTrace();
        }
        return null;
    }


    public static class NodeAndTime {
        public NodeAndTime(Long inode, Long modifiedTime) {
            this.inode = inode;
            this.modifiedTime = modifiedTime;
        }

        private Long inode, modifiedTime;

        public Long getInode() {
            return inode;
        }

        public Long getModifiedTime() {
            return modifiedTime;
        }
    }

    private static ExecutorService executorService = Executors.newCachedThreadPool();

    public static Promise<NodeAndTime, Exception, Void> getNodeAndTime(File f) {
        DeferredObject<NodeAndTime, Exception, Void> deferred = new DeferredObject<>();
        executorService.execute(() -> N.r(() -> {
            String ba = "echo $(ls -i -d '" + f.getAbsolutePath() + "')";
            String[] args = new String[]{BIN_PATH, "-c", ba};
            Process proc = new ProcessBuilder(args).start();
            String res = null;
            Long inode = null, modifiedTime = f.lastModified();
            List<String> lines;
            boolean hasFinished = false;
            while (!hasFinished) {
                try {
                    hasFinished = proc.waitFor(10, TimeUnit.SECONDS);
                    if (!hasFinished) {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                        List<String> errors = errorReader.lines().collect(Collectors.toList());
                        System.out.println("BashTools.stuffModifiedAfter.did not finish");
                    }
                    int exitValue = proc.exitValue();
                    if (exitValue == 0) {
                        System.out.println("BashTools.stuffModifiedAfter");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                        System.out.println("BashTools.stuffModifiedAfter.collecting.result");
                        lines = reader.lines().collect(Collectors.toList());
                        lines.forEach(s -> System.out.println("BashTools.getNodeAndTime.LLLL " + s));
                        String[] s = lines.get(0).split(" ");
                        if (s[0].isEmpty())
                            System.out.println("BashTools.getNodeAndTime");
                        inode = Long.parseLong(s[0]);
                        NodeAndTime nodeAndTime = new NodeAndTime(inode, modifiedTime);
                        deferred.resolve(nodeAndTime);
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
        }));
        return deferred;
    }


}
