package de.mein.drive.serialization;

import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsException;
import de.mein.drive.bash.BashToolsImpl;
import de.mein.drive.bash.BashToolsUnix;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by xor on 10/28/16.
 */
@SuppressWarnings("Duplicates")
public class BashTest {
    private static String BIN_PATH = "/bin/bash";

    private Stream<String> exec(String cmd) throws IOException, InterruptedException {
        String[] args = new String[]{BIN_PATH, "-c",
                cmd};
        System.out.println("BashToolsUnix.exec: " + cmd);
        Process proc = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        proc.waitFor();
        if (proc.exitValue()!=0){
            throw new BashToolsException(proc);
        }
        System.out.println("BashTest.exec."+proc.exitValue());
        return reader.lines();
    }

    @Test
    public void cmdNotFound() throws IOException, InterruptedException {
        Stream<String> result = exec("findr . -mindepth 1 -maxdepth 1");
        result.forEach(System.out::println);
        System.out.println("BashTest.cmdNotFound.end");
    }

    // might be deprecated below
    @Test
    public void bash1() throws IOException {
        File file = new File(System.getProperty("user.dir"));
        BashTools.getINodesOfDirectory(file);
    }

    @Test
    public void finish() throws Exception{
            // this somehow stopped working :/
            //        String[] args = new String[]{BIN_PATH, "-c", "find \"" + directory.getAbsolutePath() + "\" -mindepth 1 -newer \"" + referenceFile.getAbsolutePath() + "\""
            //                + " -prune \"" + pruneDir + "\""};
            String[] args = new String[]{BIN_PATH, "-c",
                    "find \"/home/xor/Downloads/Rishloo\""};
            List<String> result = new ArrayList<>();
            boolean hasResult = false;
            while (!hasResult) {
                String res = null;
                try {
                    Process proc = new ProcessBuilder(args).start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    reader.lines().forEach(System.out::println);
                    System.out.println("BashTest.finish");
                } catch (Exception e) {
                    System.err.println("string I got from bash: " + res);
                    e.printStackTrace();
                }
            }
    }
}
