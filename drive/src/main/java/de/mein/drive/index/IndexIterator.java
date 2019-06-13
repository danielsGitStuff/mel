package de.mein.drive.index;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

import java.util.Iterator;
import java.util.Stack;

@SuppressWarnings("Duplicates")
public class IndexIterator {
    private final Iterator<AFile> iterator;
    private final FsDao fsDao;
    private final StageDao stageDao;
    private final int rootPathLength;
    private final String rootPath;
    private final DriveDatabaseManager databaseManager;
    private Stack<AFile> fileStack = new Stack<>();
    private Stack<FsDirectory> fsDirStack = new Stack<>();
    private Stack<Stage> stageStack = new Stack<>();
    private AFile currentFile;

    public IndexIterator(Iterator<AFile> iterator, DriveDatabaseManager databaseManager) {
        this.iterator = iterator;
        this.fsDao = databaseManager.getFsDao();
        this.stageDao = databaseManager.getStageDao();
        this.rootPath = databaseManager.getDriveSettings().getRootDirectory().getPath();
        this.rootPathLength = rootPath.length();
        this.databaseManager = databaseManager;
        initStacks();
    }

    private void initStacks() {
        // init stacks with root dir
        fsDirStack = new Stack<>();
        fileStack = new Stack<>();
        N.oneLine(() -> fsDirStack.push(fsDao.getRootDirectory()));
        N.oneLine(() -> fileStack.push(databaseManager.getDriveSettings().getRootDirectory().getOriginalFile()));
        Lok.debug(fsDirStack.size());
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public AFile next() throws SqlQueriesException {

        currentFile = iterator.next();
        final boolean isDirectory = currentFile.isDirectory();
        final boolean isFile = !isDirectory;
        AFile parent = currentFile.getParentFile();

        // we are in same subdir
        if (parent.getAbsolutePath().equals(fileStack.peek().getAbsolutePath())) {
            return currentFile;
        }
        // handle the root dir itself
        if (currentFile.getAbsolutePath().equals(rootPath))
            return currentFile;
        // check if we need to empty parts of the stacks
        {
            AFile currentStackFile = currentFile;
            while (!currentStackFile.getAbsolutePath().startsWith(fileStack.peek().getAbsolutePath())) {
                // empty the file stack first
                while (fileStack.peek().getAbsolutePath().length() > currentStackFile.getAbsolutePath().length()) {
                    if (!stageStack.empty()) {
                        stageStack.pop();
                        continue;
                    }
                    if (!fsDirStack.empty())
                        fsDirStack.pop();
                }
                // check if we got to climb up the file path too
                if (!fileStack.peek().getAbsolutePath().equals(currentStackFile.getAbsolutePath())) {
                    currentStackFile = currentStackFile.getParentFile();
                }
            }

        }

        return currentFile;
    }
}
