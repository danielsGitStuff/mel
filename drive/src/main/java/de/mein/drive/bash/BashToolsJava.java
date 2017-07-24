package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * Created by xor on 7/24/17.
 */

public class BashToolsJava implements BashToolsImpl {
    @Override
    public void setBinPath(String binPath) {

    }

    @Override
    public Set<Long> getINodesOfDirectory(File file) throws IOException {
        return null;
    }

    @Override
    public Long getINodeOfFile(File file) throws IOException {
        return null;
    }

    @Override
    public void rmRf(File directory) throws IOException {

    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return null;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File("bash.test");
        File prune = new File(dir.getAbsolutePath() + File.separator + "prune");
        File file = new File(dir.getAbsolutePath() + File.separator + "file");
        dir.mkdirs();
        prune.mkdirs();
        file.createNewFile();
        BashToolsJava bashToolsJava = new BashToolsJava();
        Iterator<String> iterator = bashToolsJava.find(dir, prune);
        while (iterator.hasNext())
            System.out.println("BashToolsJava.main: " + iterator.next());
    }

    @Override
    public Iterator<String> find(File directory, File pruneDir) throws IOException {
        Stack<Iterator<File>> fileStack = new Stack<>();
        fileStack.push(Arrays.asList(directory.listFiles()).iterator());
        return new Iterator<String>() {
            String nextLine = null;

            @Override
            public boolean hasNext() {
                if (nextLine != null) {
                    return true;
                } else {
                    try {
                        Iterator<File> iterator = fileStack.peek();
                        if (iterator.hasNext()) {
                            File nextFile = iterator.next();
                            if (nextFile.isDirectory())
                                fileStack.push(Arrays.asList(nextFile.listFiles()).iterator());
                            nextLine = nextFile.getAbsolutePath();
                            return true;
                        } else {
                            fileStack.pop();
                            if (fileStack.size() == 0)
                                return false;
                            return hasNext();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            @Override
            public String next() {
                if (nextLine != null || hasNext()) {
                    String line = nextLine;
                    nextLine = null;
                    return line;
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }

    @Override
    public Iterator<String> stuffModifiedAfter(File originalFile, File pruneDir, long timeStamp) throws IOException, InterruptedException {
        return null;
    }
}
