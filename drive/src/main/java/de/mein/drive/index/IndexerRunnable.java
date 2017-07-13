package de.mein.drive.index;

import de.mein.auth.tools.Order;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by xor on 10.07.2016.
 */
public class IndexerRunnable extends AbstractIndexer {

    private final IndexWatchdogListener indexWatchdogListener;
    private SyncHandler syncHandler;
    private List<IndexListener> listeners = new ArrayList<>();
    private RootDirectory rootDirectory;
    private Order ord = new Order();

    /**
     * the @IndexWatchdogListener is somewhat special. we need it elsewhere
     *
     * @param databaseManager
     * @param indexWatchdogListener
     * @param listeners
     * @throws SqlQueriesException
     */
    public IndexerRunnable(DriveDatabaseManager databaseManager, IndexWatchdogListener indexWatchdogListener, IndexListener... listeners) throws SqlQueriesException {
        super(databaseManager);
        this.listeners.add(indexWatchdogListener);
        for (IndexListener listener : listeners)
            this.listeners.add(listener);
        this.indexWatchdogListener = indexWatchdogListener;
        this.rootDirectory = databaseManager.getDriveSettings().getRootDirectory();
    }

    public IndexerRunnable setSyncHandler(SyncHandler syncHandler) {
        this.syncHandler = syncHandler;
        return this;
    }

    public IndexWatchdogListener getIndexWatchdogListener() {
        return indexWatchdogListener;
    }

    @Override
    public void onShutDown() {
        indexWatchdogListener.shutDown();
    }

    @Override
    public void runImpl() {
        try {
            System.out.println("IndexerRunnable.runTry.roaming");
            // if root directory does not exist: create one
            FsDirectory fsRoot; //= databaseManager.getFsDao().getDirectoryById(rootDirectory.getId());
            if (rootDirectory.getId() == null) {
                fsRoot = (FsDirectory) new FsDirectory().setName("[root]").setVersion(0L);
                fsRoot.setOriginalFile(new File(rootDirectory.getPath()));
                fsRoot = (FsDirectory) databaseManager.getFsDao().insert(fsRoot);
                databaseManager.getDriveSettings().getRootDirectory().setId(fsRoot.getId().v());
            } else {
                fsRoot = databaseManager.getFsDao().getDirectoryById(rootDirectory.getId());
                if (fsRoot.getOriginal() == null) {
                    fsRoot.setOriginalFile(new File(rootDirectory.getPath()));
                }
            }
            indexWatchdogListener.watchDirectory(rootDirectory.getOriginalFile());
            try {
                fsDao.lockRead();
                Stream<String> found = BashTools.find(rootDirectory.getOriginalFile(), new File(databaseManager.getMeinDriveService().getDriveSettings().getTransferDirectoryPath()));
                initStage(DriveStrings.STAGESET_TYPE_STARTUP_INDEX, found);
                examineStage();
            } catch (Exception e) {
                e.printStackTrace();
                startedPromise.reject(e);
            } finally {
                fsDao.unlockRead();
            }


            System.out.println("IndexerRunnable.runTry.save in mem db");
            for (IndexListener listener : listeners)
                listener.done(stageSetId);
            System.out.println("IndexerRunnable.runTry.done");
            startedPromise.resolve(this);
        } catch (Exception e) {
            e.printStackTrace();
            startedPromise.reject(e);
        }
    }

    public RootDirectory getRootDirectory() {
        return rootDirectory;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + databaseManager.getDriveSettings().getRootDirectory().getPath();
    }
}
