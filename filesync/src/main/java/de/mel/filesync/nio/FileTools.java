package de.mel.filesync.nio;

import de.mel.auth.file.AbstractFile;
import de.mel.filesync.data.fs.RootDirectory;

import java.util.Stack;

public class FileTools {
    /**
     * builds a Stack of Files from the {@link RootDirectory} downwards
     *
     * @param rootDirectory
     * @param f
     * @return
     */
    public static Stack<AbstractFile> getFileStack(RootDirectory rootDirectory, AbstractFile f) {
        AbstractFile ff = AbstractFile.instance(f.absolutePath);
        Stack<AbstractFile> fileStack = new Stack<>();
        while (ff.absolutePath.length() > rootDirectory.getPath().length()) {
            fileStack.push(ff);
            ff = ff.parentFile;
        }
        return fileStack;
    }


}
