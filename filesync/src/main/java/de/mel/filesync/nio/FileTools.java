package de.mel.filesync.nio;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
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
    public static Stack<IFile> getFileStack(RootDirectory rootDirectory, IFile f) {
        IFile ff = AbstractFile.instance(f.getAbsolutePath());
        Stack<IFile> fileStack = new Stack<>();
        while (ff.getAbsolutePath().length() > rootDirectory.getPath().length()) {
            fileStack.push(ff);
            ff = ff.getParentFile();
        }
        return fileStack;
    }


}
