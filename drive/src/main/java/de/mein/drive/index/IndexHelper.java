package de.mein.drive.index;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.core.serialize.serialize.tools.OTimer;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.FsBashDetails;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class IndexHelper {

    private final DriveDatabaseManager databaseManager;
    private final long stageSetId;
    private final FsDao fsDao;
    private final StageDao stageDao;
    private final Order order;
    private final Stack<AFile> fileStack = new Stack<>();
    private final Stack<FsEntry> fsEntryStack = new Stack<>();
    private final Stack<Stage> stageStack = new Stack<>();
    private final OTimer timer1 = new OTimer("helper 1");
    private final OTimer timer2 = new OTimer("helper 2");

    private final Map<Long, Stage> stageMap = new HashMap<>();

    public IndexHelper(DriveDatabaseManager databaseManager, long stageSetId, Order order) {
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
    Stage connectToFs(AFile directory) throws SqlQueriesException {
        // remember: we always deal with directories here. that means that we can ask all DAOs for
        // directories and don't have to deal with files :)
        final int rootPathLength = databaseManager.getDriveSettings().getRootDirectory().getPath().length();
        String targetPath = directory.getAbsolutePath();
        if (targetPath.length() < rootPathLength)
            return null;
        // find out where the stacks point to
        String stackPath = databaseManager.getDriveSettings().getRootDirectory().getOriginalFile().getAbsolutePath();
        if (!fileStack.empty())
            stackPath = fileStack.peek().getAbsolutePath();

        // remove everything from the stacks that does not lead to the directory
        while (fileStack.size() > 1 && (stackPath.length() > targetPath.length() || !targetPath.startsWith(stackPath))) {
            fileStack.pop();
            stackPath = fileStack.peek().getAbsolutePath();
            if (stageStack.size() > 0) {
                Stage stage = stageStack.pop();
                if (stage != null)
                    stageMap.remove(stage.getId());
            }
            if (fsEntryStack.size() > 0) {
                fsEntryStack.pop();
            }
        }

        // calculate the parts that go onto the stacks
        Stack<AFile> remainingParts = new Stack<>();
        {
            if (!fileStack.empty()) {
                AFile currentDir = directory;
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
            fileStack.push(databaseManager.getDriveSettings().getRootDirectory().getOriginalFile());
            fsEntryStack.push(parentFs);
            stageStack.push(stageParent);
        }
        while (!remainingParts.empty()) {
            final AFile part = remainingParts.pop();

            if (parentFs != null) {
                parentFs = fsDao.getSubDirectoryByName(parentFs.getId().v(), part.getName());
            }
            if (stageParent == null && parentFs != null) {
                stageParent = stageDao.getStageParentByFsId(stageSetId, parentFs.getId().v());
            } else if (stageParent != null) {
                Stage newStageParent = stageDao.getSubStageByName(stageParent.getId(), part.getName());
                if (newStageParent == null) {
                    //this should not happen
                    newStageParent = new Stage();
                    stageDao.insert(newStageParent);
//                    stageMap.put(newStageParent.getId(), newStageParent);
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

    void fastBoot(AFile file, FsEntry fsEntry, Stage stage) {
        if (databaseManager.getDriveSettings().getFastBoot()) {
            try {
                //todo debug
                if (stage.getNamePair().equalsValue("same1.txt"))
                    Eva.flagAndRun("looo", 2, () -> Lok.debug());
                FsBashDetails fsBashDetails = BashTools.getFsBashDetails(file);
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
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
