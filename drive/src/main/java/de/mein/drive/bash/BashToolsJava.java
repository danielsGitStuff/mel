package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.*;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;

/**
 * Created by xor on 7/24/17.
 */

public class BashToolsJava implements BashToolsImpl {
    @Override
    public void setBinPath(String binPath) {

    }

    @Override
    public Set<Long> getINodesOfDirectory(AFile file) throws IOException {
        return null;
    }

    @Override
    public ModifiedAndInode getModifiedAndINodeOfFile(AFile file) throws IOException {
        return null;
    }

    @Override
    public void rmRf(AFile directory) throws IOException {

    }

    @Override
    public List<AFile> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException {
        return null;
    }

    //public static int max;
    //public static int depth = 0;

    public static void main(String[] args) throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        AFile dir = AFile.instance("bash.test");
        AFile prune =AFile.instance(dir.getAbsolutePath() + File.separator + "prune");
        File file = new File(dir.getAbsolutePath() + File.separator + "file");
        dir.mkdirs();
        prune.mkdirs();
        for (int i = 0; i < 2100; i++) {
            new File(prune.getAbsolutePath() + File.separator + i).createNewFile();
        }
        file.createNewFile();
        BashToolsJava bashToolsJava = new BashToolsJava();
        Iterator<AFile> iterator = bashToolsJava.find(dir, prune);
        while (iterator.hasNext())
            Lok.debug("BashToolsJava.main: " + iterator.next());
        //Lok.debug("BashToolsJava.main.max: " + max);
    }

    @Override
    public Iterator<AFile> find(AFile directory, AFile pruneDir) throws IOException {
        Stack<Iterator<AFile>> fileStack = new Stack<>();
        String prunePath = pruneDir.getAbsolutePath();
        Lok.debug("BashToolsJava.find.prune: " + prunePath);
        fileStack.push(Arrays.asList(directory.listContent()).iterator());
        return new Iterator<AFile>() {
            String nextLine = null;

            private void fastForward() {
                Iterator<AFile> iterator = fileStack.peek();
                while (iterator != null) {
                    while (iterator.hasNext()) {
                        AFile f = iterator.next();
                        if (!f.getAbsolutePath().startsWith(prunePath)) {
                            nextLine = f.getAbsolutePath();
                            return;
                        }
                    }
                    fileStack.pop();
                    if (fileStack.size() != 0) {
                        iterator = fileStack.peek();
                    } else
                        return;
                }
            }

            @Override
            public boolean hasNext() {
                //inc();
                if (nextLine != null) {
                    if (nextLine.startsWith(prunePath)) {
                        nextLine = null;
                        fastForward();
                        return nextLine != null;
                    }
                    //dec();
                    return true;
                } else {
                    try {
                        Iterator<AFile> iterator = fileStack.peek();
                        if (iterator.hasNext()) {
                            AFile nextFile = iterator.next();
                            if (nextFile.isDirectory())
                                fileStack.push(Arrays.asList(nextFile.listContent()).iterator());
                            nextLine = nextFile.getAbsolutePath();
                            if (nextLine.startsWith(prunePath)) {
                                return hasNext();
                            }
                            //dec();
                            return true;
                        } else {
                            fileStack.pop();
                            if (fileStack.size() == 0) {
                                //dec();
                                return false;
                            }
                            return hasNext();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }

//            private void dec() {
//                depth--;
//            }
//
//            private void inc() {
//                depth++;
//                if (depth > max)
//                    max = depth;
//            }

            @Override
            public AFile next() {
                if (nextLine != null || hasNext()) {
                    String line = nextLine;
                    nextLine = null;
                    return AFile.instance(line);
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    @Override
    public Promise<Long, Exception, Void> getInode(AFile f) {
        return null;
    }

    @Override
    public Iterator<AFile> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void mkdir(AFile dir) throws IOException {
        int i = 0;
        while (!dir.exists()) {
            dir.mkdirs();
            Lok.debug("BashToolsJava.mkdir."+i);
        }
    }

    @Override
    public boolean mv(File source, File target) throws IOException {
        System.err.println("BashToolsJava.mv.NOT:IMPLEMENTED");
        return false;
    }
}
