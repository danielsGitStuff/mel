package de.mein.drive.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by xor on 10/28/16.
 */
public class BashTools {
    public static Set<Long> getINodesOfDirectory(File file) throws IOException {
        String[] args = new String[]{"/bin/bash", "-c", "find share/ -printf \"%p\\n\" | tail -n +2 | xargs -d '\\n' stat -c %i"};
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
        String[] args = new String[]{"/bin/bash", "-c", "stat -c %i \"" + file.getAbsolutePath() + "\""};
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
        String[] args = new String[]{"/bin/bash", "-c", "rm -rf \"" + directory.getAbsolutePath() + "\""};
        Process proc = new ProcessBuilder(args).start();
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
        String ba = "echo $(date +%s -r  '" + f.getAbsolutePath() + "') $(ls -i -d '" + f.getAbsolutePath() + "')";
        String[] args = new String[]{"/bin/bash", "-c", ba};
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String res = reader.readLine();
        String[] s = res.split(" ");
        Long modifiedTime = Long.parseLong(s[0]);
        Long inode = Long.parseLong(s[1]);
        NodeAndTime nodeAndTime = new NodeAndTime(inode, modifiedTime);
        return nodeAndTime;
    }

    public static void main(String[] args) throws IOException {
        File f = new File("/home/xor/Downloads/cm-13.0-20160925-NIGHTLY-klte-recovery.img");
        NodeAndTime nodeAndTime = getNodeAndTime(f);
        System.out.println(nodeAndTime.getInode());
        System.out.println(nodeAndTime.getModifiedTime());
    }
}
