package de.mein.drive.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by xor on 10/28/16.
 */
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


    public static List<String> stuffModifiedAfter(File referenceFile, File directory) throws IOException {
        String[] args = new String[]{BIN_PATH, "-c", "find \"" + directory.getAbsolutePath() + "\" -mindepth 1 -newer \"" + referenceFile.getAbsolutePath() + "\""};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String res = null;
        List<String> result = new ArrayList<>();
        try {
            proc.waitFor();
            reader.lines().forEach(result::add);
            return result;
            //res = reader.readLine();
            //System.out.println("BashTools.stuffModifiedAfter.parsing: " + res);
            //String[] s = res.split(" ");
        } catch (InterruptedException e) {
            System.err.println("string I got from bash: " + res);
            e.printStackTrace();
        }
        return result;
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

    public static NodeAndTime getNodeAndTime(File f) throws IOException {
        String ba = "echo $(ls -i -d '" + f.getAbsolutePath() + "')";
        String[] args = new String[]{BIN_PATH, "-c", ba};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String res = null;
        Long inode = null, modifiedTime = f.lastModified();
        try {
            proc.waitFor();
            res = reader.readLine();
            System.out.println("BashTools.getNodeAndTime.parsing: " + res);
            String[] s = res.split(" ");
            inode = Long.parseLong(s[0]);
            NodeAndTime nodeAndTime = new NodeAndTime(inode, modifiedTime);
            return nodeAndTime;
        } catch (InterruptedException e) {
            System.err.println("string I got from bash: " + res);
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        File f = new File("/home/xor/Downloads/cm-13.0-20160925-NIGHTLY-klte-recovery.img");
        NodeAndTime nodeAndTime = getNodeAndTime(f);
        System.out.println(nodeAndTime.getInode());
        System.out.println(nodeAndTime.getModifiedTime());
    }
}
