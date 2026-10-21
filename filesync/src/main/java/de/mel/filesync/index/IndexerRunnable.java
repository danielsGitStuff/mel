package de.mel.filesync.index;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.Order;
import de.mel.auth.tools.lock2.P;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.core.serialize.serialize.tools.OTimer;
import de.mel.filesync.bash.AutoKlausIterator;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.index.watchdog.FileWatcher;
import de.mel.filesync.service.sync.SyncHandler;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsDirectory;
import de.mel.sql.ISQLQueries;
import de.mel.sql.SqlQueriesException;
import fun.with.promise.Promise;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 10.07.2016.
 */
public class IndexerRunnable extends AbstractIndexer {

    private final FileWatcher fileWatcher;
    private SyncHandler syncHandler;
    private List<IndexListener> listeners = new ArrayList<>();
    private RootDirectory rootDirectory;
    private Order ord = new Order();
    private volatile boolean fullScanRequested = false;

    /**
     * the next run looks at every file, not only at what changed since the last one
     */
    public void requestFullScan() {
        fullScanRequested = true;
    }

    /**
     * Said to `find` when it looks for what changed. More than the rounding of any file system.
     */
    static final long MARGIN_MS = 2000;

    /**
     * @return the time to look for changes since, null if every file has to be looked at: the setting says so, it was requested, there was no run before, the clock
     * went back since or this platform's find cannot take a time
     */
    private Long incrementalSince(long now) {
        FileSyncSettings settings = databaseManager.getFileSyncSettings();
        Long last = settings.getLastScanTime();
        if (fullScanRequested || !settings.getFastBoot() || last == null || last > now || !BashTools.Companion.canFindByTime())
            return null;
        return last - MARGIN_MS;
    }


    /**
     * the @IndexWatchdogListener is somewhat special. we need it elsewhere
     *
     * @param databaseManager
     * @param fileWatcher
     * @param listeners
     * @throws SqlQueriesException
     */
    public IndexerRunnable(FileSyncDatabaseManager databaseManager, FileWatcher fileWatcher, IndexListener... listeners) throws SqlQueriesException {
        super(databaseManager);
        this.listeners.add(fileWatcher);
        for (IndexListener listener : listeners)
            this.listeners.add(listener);
        this.fileWatcher = fileWatcher;
        this.rootDirectory = databaseManager.getFileSyncSettings().getRootDirectory();
    }

    public IndexerRunnable setSyncHandler(SyncHandler syncHandler) {
        this.syncHandler = syncHandler;
        return this;
    }

    public FileWatcher getFileWatcher() {
        return fileWatcher;
    }

    @Override
    public Promise<Void, Void> onShutDown() {
        return fileWatcher.shutDown();
    }

    @Override
    public void runImpl() {
        Long scanned = null;
        try {
            Lok.debug("roaming...");
            // if root directory does not exist: create one
            FsDirectory fsRoot;
            if (rootDirectory.getId() == null) {
                fsRoot = (FsDirectory) new FsDirectory().setName("").setVersion(0L).setPath("").setDepth(0);
                fsRoot.setOriginalFile(AbstractFile.instance(rootDirectory.getOriginalFile()));
                // assume that the root dir is empty -> the first indexed stageset will have no delta to the fs table if the root dir is empty.
                // this will avoid having a conflict if the clients root is empty but the servers is not.
                FsBashDetails details = BashTools.Companion.getFsBashDetails(AbstractFile.instance(rootDirectory.getOriginalFile()));
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
                    fsRoot.setOriginalFile(AbstractFile.instance(rootDirectory.getOriginalFile()));
                }
            }
            BunchOfLocks bunchOfLocks = P.confine(P.read(fsDao));
            try {

                fileWatcher.watchDirectory(rootDirectory.getOriginalFile());
                ISQLQueries sqlQueries = stageDao.getSqlQueries();
//                OTimer timerFind = new OTimer("bash.find").start();
                OTimer timerInit = new OTimer("init stageset");
                // taken before anything is looked at: what changes meanwhile is found next time
                final long scanStart = System.currentTimeMillis();
                final Long since = incrementalSince(scanStart);
                IFile transferDirectory = databaseManager.getMelFileSyncService().getFileSyncSettings().getTransferDirectory();
                Lok.debug(since == null ? "index: every file" : "index: what changed since " + since);
                try (AutoKlausIterator<IFile> found = since == null
                        ? BashTools.Companion.find(rootDirectory.getOriginalFile(), transferDirectory)
                        : BashTools.Companion.stuffModifiedAfter(rootDirectory.getOriginalFile(), transferDirectory, since)) {
                    Lok.debug("starting stageset initialization");
                    Lok.error("TRANSACTION DISABLED!!!!!");
                    Lok.error("TRANSACTION DISABLED!!!!!");
                    Lok.error("TRANSACTION DISABLED!!!!!");
                    Lok.error("TRANSACTION DISABLED!!!!!");
                    Lok.error("TRANSACTION DISABLED!!!!!");
//                    sqlQueries.beginTransaction();
                    timerInit.start();
                    initStage(FileSyncStrings.STAGESET_SOURCE_FS, found, fileWatcher, databaseManager.getFileSyncSettings().getLastSyncedVersion());
                    OTimer timerExamine = new OTimer("examine stageset").start();
                    examineStage();
                    timerExamine.stop().print();
                    finishStaging();
//                    sqlQueries.commit();
                    scanned = scanStart;
                } catch (Exception e) {
                    e.printStackTrace();
                    sqlQueries.rollback();
                } finally {
                    timerInit.stop().print().reset();
                }

                if (initialIndexConflictHelper != null) {
                    boolean conflicts = initialIndexConflictHelper.onDone(bunchOfLocks, this);
                    if (!conflicts)
                        initialIndexConflictHelper = null;
                }
                fullScanRequested = false;
            } catch (Exception e) {
                e.printStackTrace();
                rejectStarted(e);
                return;
            } finally {
                bunchOfLocks.end();
            }


            Lok.debug("save in  db");
            bunchOfLocks = P.confine(fsDao);
            for (IndexListener listener : listeners)
                listener.done(examinedStageSetId, bunchOfLocks);
            bunchOfLocks.end();
            // the next start looks for what changed from here on. Only a run that went through counts: after a failure the old time stays and the next run does more, never less
            if (scanned != null) {
                databaseManager.getFileSyncSettings().setLastScanTime(scanned).save();
            }
            Lok.debug("indexing done");
            if (initialIndexConflictHelper == null && !startedPromise.hasResult())
                resolveStarted();
        } catch (Exception e) {
            e.printStackTrace();
            if (!startedPromise.hasResult())
                rejectStarted(e);
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
        fileWatcher.stop();
    }
}
