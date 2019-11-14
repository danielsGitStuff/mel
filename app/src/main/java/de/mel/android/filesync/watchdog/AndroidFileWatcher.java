package de.mel.android.filesync.watchdog;

import android.os.FileObserver;

import java.io.File;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.filesync.index.watchdog.FileWatcher;
import de.mel.auth.tools.WatchDogTimer;
import de.mel.sql.RWLock;
import org.jdeferred.Promise;

/**
 * Created by xor on 2/6/17.
 */

public class AndroidFileWatcher extends FileWatcher {
    private boolean watchesRoot = false;
    private FileObserver fileObserver;
    private WatchDogTimer watchDogTimer;

    public AndroidFileWatcher(MelFileSyncService melFileSyncService) {
        super(melFileSyncService);
        this.setStageIndexer(melFileSyncService.getStageIndexer());
        watchDogTimer = new WatchDogTimer("AndroidWatchDogListener",this, 20, 100, 100);
        watchDirectory(melFileSyncService.getFileSyncSettings().getRootDirectory().getOriginalFile());
    }

    @Override
    public void watchDirectory(AbstractFile dir) {
//        Lok.debug("AndroidWatchdogListener.watchDirectory");
        if (!watchesRoot)
            try {
                watchesRoot = true;
                fileObserver = new FileObserver(dir.absolutePath) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null)
                            try {
                                path = melFileSyncService.getFileSyncSettings().getRootDirectory().getOriginalFile().absolutePath + File.separator + path;
                                AbstractFile file = AbstractFile.instance(path);
                                analyzeEvent(event, file);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } finally {
                                ignoredSemaphore.release();
                            }
                    }
                };
                fileObserver.startWatching();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private void analyzeEvent(final int event, AbstractFile path) throws InterruptedException {
        if (FileObserver.CLOSE_WRITE == event
                || FileObserver.CREATE == event
                || FileObserver.DELETE == event
                || FileObserver.MOVED_TO == event
                || FileObserver.MOVED_FROM == event) {
            ignoredSemaphore.acquire();
            if (!ignoredMap.containsKey(path)) {
                Lok.debug("AndroidWatchdogListener.analyzeEvent: " + path);
                pathCollection.addPath(path);
                if (melFileSyncService.getMelAuthService().getPowerManager().heavyWorkAllowed()) {
                    watchDogTimer.start();
                }else
                    surpressEvent();
            }
        }
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {

        return null;
    }

    @Override
    public void runImpl() {
        Lok.debug("AndroidWatchdogListener.run.nothing to do :)");
        RWLock lock = new RWLock();
        lock.lockWrite();
        while (!Thread.currentThread().isInterrupted()) {
            lock.lockWrite();
        }
        Lok.debug("AndroidWatchdogListener.run.stop");
        fileObserver.stopWatching();
    }

    @Override
    public void onTimerStopped() {
        super.onTimerStopped();
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

}
