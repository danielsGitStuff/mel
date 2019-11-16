package de.mel.filesync.index;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.Order;
import de.mel.core.serialize.serialize.tools.OTimer;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsEntry;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.SqlQueriesException;

import java.io.IOException;
import java.util.Stack;

public class IndexHelper {

    private final FileSyncDatabaseManager databaseManager;
    private final long stageSetId;
    private final FsDao fsDao;
    private final StageDao stageDao;
    private final Order order;
    private final Stack<IFile> fileStack = new Stack<>();
    private final Stack<FsEntry> fsEntryStack = new Stack<>();
    private final Stack<Stage> stageStack = new Stack<>();
    private final OTimer timer1 = new OTimer("helper 1");
    private final OTimer timer2 = new OTimer("helper 2");


    public IndexHelper(FileSyncDatabaseManager databaseManager, long stageSetId, Order order) {
        this.databaseManager = databaseManager;
        this.stageSetId = stageSetId;
        this.fsDao = databaseManager.getFsDao();
        this.stageDao = databaseManager.getStageDao();
        this.order = order;
//        N.oneLine(() -> {
//            fileStack.push(databaseManager.getDriveSettings().getRootDirectory().getOriginalFile());
//            fsEntryStack.push(fsDao.getRootDirectory());
//        });
    }

    /**
     * here we build a path to the directory.
     * we build the path on two stacks: one for everything that already exists in FS
     * and one filled with Stages. they always stay equal in size and are filled with null if no db entry is present
     *
     * @param directory
     * @return
     * @throws SqlQueriesException
     */
    Stage connectToFs(IFile directory) throws SqlQueriesException {
        // remember: we always deal with directories here. that means that we can ask all DAOs for
        // directories and don't have to deal with files :)
        final int rootPathLength = databaseManager.getFileSyncSettings().getRootDirectory().getPath().length();
        String targetPath = directory.getAbsolutePath();
        if (targetPath.length() < rootPathLength)
            return null;
        // find out where the stacks point to
        String stackPath = databaseManager.getFileSyncSettings().getRootDirectory().getOriginalFile().getAbsolutePath();
        if (!fileStack.empty())
            stackPath = fileStack.peek().getAbsolutePath();

        // remove everything from the stacks that does not lead to the directory
        while (fileStack.size() > 1 && (stackPath.length() > targetPath.length() || !targetPath.startsWith(stackPath))) {
            fileStack.pop();
            stackPath = fileStack.peek().getAbsolutePath();
            if (stageStack.size() > 0) {
                Stage stage = stageStack.pop();
            }
            if (fsEntryStack.size() > 0) {
                fsEntryStack.pop();
            }
        }

        // calculate the parts that go onto the stacks
        Stack<IFile> remainingParts = new Stack<>();
        {
            if (!fileStack.empty()) {
                IFile currentDir = directory;
                while (currentDir.getAbsolutePath().length() >= fileStack.peek().getAbsolutePath().length()
                        && !currentDir.getAbsolutePath().equals(fileStack.peek().getAbsolutePath())) {
                    remainingParts.push(currentDir);
                    currentDir = currentDir.getParentFile();
                }
            }
        }
        // one of those must be not null!!! otherwise we produce orphans!
        Stage stageParent = stageStack.empty() ? null : stageStack.peek();
        FsEntry parentFs = fsEntryStack.empty() ? null : fsEntryStack.peek();
        if (fileStack.empty()) {
            parentFs = fsDao.getRootDirectory();
            stageParent = stageDao.getStageByFsId(parentFs.getId().v(), stageSetId);
            fileStack.push(databaseManager.getFileSyncSettings().getRootDirectory().getOriginalFile());
            fsEntryStack.push(parentFs);
            stageStack.push(stageParent);
        }
        while (!remainingParts.empty()) {
            final IFile part = remainingParts.pop();

            if (parentFs != null) {
                parentFs = fsDao.getSubDirectoryByName(parentFs.getId().v(), part.getName());
            }
            if (stageParent == null && parentFs != null) {
                stageParent = stageDao.getStageParentByFsId(stageSetId, parentFs.getId().v());
            } else if (stageParent != null) {
                Stage newStageParent = stageDao.getSubStageByName(stageParent.getId(), part.getName());
                if (newStageParent == null) {
                    /*
                     * this may happen if the output of find is not hierarchicly coherent:
                     * /home
                     * /home/user/subdir/file.txt
                     */
                    newStageParent = new Stage()
                            .setName(part.getName())
                            .setOrder(order.ord())
                            .setStageSet(stageSetId)
                            .setDeleted(false);
                    newStageParent.setParentId(stageParent.getId());
                    newStageParent.setFsParentId(stageParent.getFsId());
                    stageDao.insert(newStageParent);
                }
                stageParent = newStageParent;
            }
            fileStack.push(part);
            fsEntryStack.push(parentFs);
            stageStack.push(stageParent);

        }
        if (stageStack.empty())
            return null;
        return stageStack.peek();
    }

    void fastBoot(IFile file, FsEntry fsEntry, Stage stage) {
        if (databaseManager.getFileSyncSettings().getFastBoot()) {
            try {
                FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(file);
                if (fsEntry.getModified().equalsValue(fsBashDetails.getModified())
                        && fsEntry.getiNode().equalsValue(fsBashDetails.getiNode())
                        && ((fsEntry.getIsDirectory().v() && file.isDirectory()) || fsEntry.getSize().equalsValue(file.length()))) {
                    stage.setiNode(fsBashDetails.getiNode());
                    stage.setModified(fsBashDetails.getModified());
                    stage.setContentHash(fsEntry.getContentHash().v());
                    stage.setSize(fsEntry.getSize().v());
                    stage.setSynced(true);
                    if (fsBashDetails.isSymLink())
                        stage.setSymLink(fsBashDetails.getSymLinkTarget());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
