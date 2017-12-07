package de.mein.drive.nio;

import com.sun.istack.internal.NotNull;
import de.mein.drive.data.fs.RootDirectory;

import java.io.File;
import java.util.Stack;

public class FileTools {
    /**
     * builds a Stack of Files from the {@link RootDirectory} downwards
     *
     * @param rootDirectory
     * @param f
     * @return
     */
    public static Stack<File> getFileStack( RootDirectory rootDirectory, File f) {
        File ff = new File(f.getAbsolutePath());
        Stack<File> fileStack = new Stack<>();
        while (ff.getAbsolutePath().length() > rootDirectory.getPath().length()) {
            fileStack.push(ff);
            ff = ff.getParentFile();
        }
        return fileStack;
    }
}
