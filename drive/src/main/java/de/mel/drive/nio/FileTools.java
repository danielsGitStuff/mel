package de.mel.drive.nio;

import de.mel.auth.file.AFile;
import de.mel.drive.data.fs.RootDirectory;

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
    public static Stack<AFile> getFileStack(RootDirectory rootDirectory, AFile f) {
        AFile ff = AFile.instance(f.getAbsolutePath());
        Stack<AFile> fileStack = new Stack<>();
        while (ff.getAbsolutePath().length() > rootDirectory.getPath().length()) {
            fileStack.push(ff);
            ff = ff.getParentFile();
        }
        return fileStack;
    }


}
