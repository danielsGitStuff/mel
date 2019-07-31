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
        N.oneLine(() -> {
            fileStack.push(databaseManager.getDriveSettings().getRootDirectory().getOriginalFile());
            fsEntryStack.push(fsDao.getRootDirectory());
        });
    }


    Stage connectToFs(AFile directory) throws SqlQueriesException {
        // remember: we always deal with directories here. that means that we can ask all DAOs for
        // directories and don't have to deal with files :)
        final int rootPathLength = databaseManager.getDriveSettings().getRootDirectory().getPath().length();
        String targetPath = directory.getAbsolutePath();
        if (targetPath.length() < rootPathLength)
            return null;
        String stackPath = fileStack.peek().getAbsolutePath();

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
            AFile currentDir = directory;
            while (currentDir.getAbsolutePath().length() >= fileStack.peek().getAbsolutePath().length()
                    && !currentDir.getAbsolutePath().equals(fileStack.peek().getAbsolutePath())) {
                remainingParts.push(currentDir);
                currentDir = currentDir.getParentFile();
            }
        }

        while (!remainingParts.empty()) {
            final AFile part = remainingParts.pop();
            fileStack.push(part);

            // fs comes first. if the peek element on stack is null all of the (fs) successors are null as well.
            // otherwise not every fs entry was connected to the root directory.
            FsEntry peekFsEntry = null;
            final FsEntry previousFsPeek = fsEntryStack.peek();
            {
                FsEntry partToAdd = null;
                if (previousFsPeek != null) {
                    partToAdd = fsDao.getSubDirectoryByName(previousFsPeek.getId().v(), part.getName());
                }
                fsEntryStack.push(partToAdd);
                peekFsEntry = partToAdd;
            }
            {
                // push to stage stack, respect probably added fs entry.
                // at this point either the fs stack or stage stack must have a non null peek element.
                // otherwise the connection to the root element is lost.
                Stage peekStage = null;
                if (!stageStack.empty())
                    peekStage = stageStack.peek();

                // check for disconnection
                if (peekFsEntry == null && peekStage == null) {
                    Lok.error("connection to the root directory has been lost.");
                }

                Stage alreadyStaged = null;
                if (peekFsEntry != null) {
                    alreadyStaged = stageDao.getStageByFsId(peekFsEntry.getId().v(), stageSetId);
                }
                if (alreadyStaged == null && peekStage != null) {
                    alreadyStaged = stageDao.getStageByStageSetParentName(stageSetId, peekStage.getId(), part.getName());
                }

                Stage stageToAdd;
                if (alreadyStaged != null)
                    stageToAdd = alreadyStaged;
                else {
                    stageToAdd = new Stage()
                            .setName(part.getName())
                            .setIsDirectory(true)
                            .setStageSet(stageSetId)
                            .setOrder(order.ord());
//                            .setRelativePath(targetPath.substring(rootPathLength));
                }
                stageToAdd.setDeleted(!part.exists());

                if (peekFsEntry != null) {
                    stageToAdd.setFsId(peekFsEntry.getId().v());
                }
                // set parent ids
                if (peekStage != null) {
                    stageToAdd.setParentId(peekStage.getParentId())
                            .setFsParentId(peekStage.getFsId());
                }
                if (previousFsPeek != null) {
                    stageToAdd.setFsParentId(previousFsPeek.getId().v());
                }
                // update database
                if (stageToAdd.getIdPair().isNull()) {
                    stageDao.insert(stageToAdd);
                }
                stageStack.push(stageToAdd);
                stageMap.put(stageToAdd.getId(), stageToAdd);
            }
        }
        // if nothing is on the stage stack here, you are working on the root dir.
        // and that has not been created yet (as a stage).
        if (stageStack.empty()) {
            Stage stageToAdd = new Stage()
                    .setStageSet(stageSetId);
            N.oneLine(() -> {
                FsEntry fsRoot = fsDao.getRootDirectory();
                stageToAdd.setFsId(fsRoot.getId().v())
                        .setName(fsRoot.getName().v())
                        .setIsDirectory(true)
                        .setOrder(order.ord())
                        .setDeleted(false);
            });
            stageDao.insert(stageToAdd);
            stageStack.push(stageToAdd);
            stageMap.put(stageToAdd.getId(), stageToAdd);
        }
        return stageStack.peek();
    }

    void fastBoot(AFile file, FsEntry fsEntry, Stage stage) {
        if (databaseManager.getDriveSettings().getFastBoot()) {
            try {
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
