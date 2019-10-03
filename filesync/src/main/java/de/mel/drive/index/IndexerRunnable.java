package de.mel.drive.index;

import de.mel.Lok;
import de.mel.auth.file.AFile;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.Order;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.core.serialize.serialize.tools.OTimer;
import de.mel.drive.bash.AutoKlausIterator;
import de.mel.drive.bash.BashTools;
import de.mel.drive.bash.FsBashDetails;
import de.mel.drive.data.FileSyncStrings;
import de.mel.drive.data.fs.RootDirectory;
import de.mel.drive.index.watchdog.IndexWatchdogListener;
import de.mel.drive.service.sync.SyncHandler;
import de.mel.drive.sql.FileSyncDatabaseManager;
import de.mel.drive.sql.FsDirectory;
import de.mel.sql.ISQLQueries;
import de.mel.sql.SqlQueriesException;
import org.jdeferred.Promise;

import java.util.ArrayList;
import java.util.List;

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
    public IndexerRunnable(FileSyncDatabaseManager databaseManager, IndexWatchdogListener indexWatchdogListener, IndexListener... listeners) throws SqlQueriesException {
        super(databaseManager);
        this.listeners.add(indexWatchdogListener);
        for (IndexListener listener : listeners)
            this.listeners.add(listener);
        this.indexWatchdogListener = indexWatchdogListener;
        this.rootDirectory = databaseManager.getFileSyncSettings().getRootDirectory();
    }

    public IndexerRunnable setSyncHandler(SyncHandler syncHandler) {
        this.syncHandler = syncHandler;
        return this;
    }

    public IndexWatchdogListener getIndexWatchdogListener() {
        return indexWatchdogListener;
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        return indexWatchdogListener.shutDown();
    }

    @Override
    public void runImpl() {
        try {
            Lok.debug("roaming...");
            // if root directory does not exist: create one
            FsDirectory fsRoot;
            if (rootDirectory.getId() == null) {
                fsRoot = (FsDirectory) new FsDirectory().setName("[root]").setVersion(0L);
                fsRoot.setOriginalFile(AFile.instance(rootDirectory.getOriginalFile()));
                // assume that the root dir is empty -> the first indexed stageset will have no delta to the fs table if the root dir is empty.
                // this will avoid having a conflict if the clients root is empty but the servers is not.
                FsBashDetails details = BashTools.getFsBashDetails(AFile.instance(rootDirectory.getOriginalFile()));
                fsRoot.calcContentHash();
                fsRoot.setModified(details.getModified())
                        .setCreated(details.getCreated())
                        .setInode(details.getiNode());
                fsRoot = (FsDirectory) databaseManager.getFsDao().insert(fsRoot);
                databaseManager.getFileSyncSettings().getRootDirectory().setId(fsRoot.getId().v());
                // save so the root dir won't be created again in case something goes wrong while booting
                databaseManager.getFileSyncSettings().save();
            } else {
                fsRoot = databaseManager.getFsDao().getDirectoryById(rootDirectory.getId());
                if (fsRoot.getOriginal() == null) {
                    fsRoot.setOriginalFile(AFile.instance(rootDirectory.getOriginalFile()));
                }
            }
            Warden warden = P.confine(P.read(fsDao));
            try {

                indexWatchdogListener.watchDirectory(rootDirectory.getOriginalFile());
                ISQLQueries sqlQueries = stageDao.getSqlQueries();
                OTimer timerFind = new OTimer("bash.find").start();
                OTimer timerInit = new OTimer("init stageset");
                try (AutoKlausIterator<AFile<?>> found = BashTools.find(rootDirectory.getOriginalFile(), databaseManager.getMelFileSyncService().getFileSyncSettings().getTransferDirectory())) {
                    Lok.debug("starting stageset initialization");
//                    Lok.error("TRANSACTION DISABLED!!!!!");
//                    Lok.error("TRANSACTION DISABLED!!!!!");
//                    Lok.error("TRANSACTION DISABLED!!!!!");
//                    Lok.error("TRANSACTION DISABLED!!!!!");
//                    Lok.error("TRANSACTION DISABLED!!!!!");
                    sqlQueries.beginTransaction();
                    timerInit.start();
                    initStage(FileSyncStrings.STAGESET_SOURCE_FS, found, indexWatchdogListener, databaseManager.getFileSyncSettings().getLastSyncedVersion());
                } catch (Exception e) {
                    //todo abort transaction
                    e.printStackTrace();
                    sqlQueries.rollback();
                } finally {
                    timerInit.stop().print().reset();
                }

                timerFind.stop().print();

                OTimer timerExamine = new OTimer("examine stageset").start();
                Eva.flagAndRun("ii!", 2, () -> Lok.debug());
                examineStage();
                sqlQueries.commit();
                timerExamine.stop().print();
                if (initialIndexConflictHelper != null) {
                    boolean conflicts = initialIndexConflictHelper.onDone(warden, this);
                    if (!conflicts)
                        initialIndexConflictHelper = null;
                }
                fastBooting = true;
            } catch (Exception e) {
                e.printStackTrace();
                startedPromise.reject(e);
                return;
            } finally {
                warden.end();
            }


            Lok.debug("save in  db");
            warden = P.confine(fsDao);
            for (IndexListener listener : listeners)
                listener.done(stageSetId, warden);
            warden.end();
            Lok.debug("indexing done");
            if (initialIndexConflictHelper == null && !startedPromise.isResolved())
                startedPromise.resolve(this);
        } catch (Exception e) {
            e.printStackTrace();
            if (!startedPromise.isResolved())
                startedPromise.reject(e);
        }
    }

    public RootDirectory getRootDirectory() {
        return rootDirectory;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + databaseManager.getFileSyncSettings().getRootDirectory().getPath();
    }

    public void stop() {
        indexWatchdogListener.stop();
    }
}
