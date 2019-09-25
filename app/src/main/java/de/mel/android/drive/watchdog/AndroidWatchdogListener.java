package de.mel.android.drive.watchdog;

import android.os.FileObserver;

import java.io.File;

import de.mel.Lok;
import de.mel.auth.file.AFile;
import de.mel.drive.service.MelDriveService;
import de.mel.drive.index.watchdog.IndexWatchdogListener;
import de.mel.auth.tools.WatchDogTimer;
import de.mel.sql.RWLock;
import org.jdeferred.Promise;

/**
 * Created by xor on 2/6/17.
 */

public class AndroidWatchdogListener extends IndexWatchdogListener {
    private boolean watchesRoot = false;
    private FileObserver fileObserver;
    private WatchDogTimer watchDogTimer;

    public AndroidWatchdogListener(MelDriveService melDriveService) {
        super(melDriveService);
        this.setStageIndexer(melDriveService.getStageIndexer());
        watchDogTimer = new WatchDogTimer("AndroidWatchDogListener",this, 20, 100, 100);
        watchDirectory(melDriveService.getDriveSettings().getRootDirectory().getOriginalFile());
    }

    @Override
    public void watchDirectory(AFile dir) {
//        Lok.debug("AndroidWatchdogListener.watchDirectory");
        if (!watchesRoot)
            try {
                watchesRoot = true;
                fileObserver = new FileObserver(dir.getAbsolutePath()) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null)
                            try {
                                path = melDriveService.getDriveSettings().getRootDirectory().getOriginalFile().getAbsolutePath() + File.separator + path;
                                AFile file = AFile.instance(path);
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

    private void analyzeEvent(final int event, AFile path) throws InterruptedException {
        if (FileObserver.CLOSE_WRITE == event
                || FileObserver.CREATE == event
                || FileObserver.DELETE == event
                || FileObserver.MOVED_TO == event
                || FileObserver.MOVED_FROM == event) {
            ignoredSemaphore.acquire();
            if (!ignoredMap.containsKey(path)) {
                Lok.debug("AndroidWatchdogListener.analyzeEvent: " + path);
                pathCollection.addPath(path);
                if (melDriveService.getMelAuthService().getPowerManager().heavyWorkAllowed()) {
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
