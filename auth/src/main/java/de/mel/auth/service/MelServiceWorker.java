package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.jobs.Job;
import de.mel.auth.tools.CountdownLock;
import org.jdeferred.Promise;

import java.io.File;
import java.util.LinkedList;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MelServiceWorker extends MelService {
    protected LinkedList<Job> jobs = new LinkedList<>();
    private CountdownLock initLock = new CountdownLock(1);

    public MelServiceWorker(MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String uuid, Bootloader.BootLevel bootLevel) {
        super(melAuthService, workingDirectory, serviceTypeId, uuid, bootLevel);
    }


    @Override
    public void runImpl() {
        try {
//            initLock.lock();
            while (!isStopped()) {
                queueLock.lockWrite();
                Job job = jobs.poll();
                queueLock.unlockWrite();
                if (job != null) {
                    try {
                        melAuthService.getPowerManager().wakeLock(this);
                        workWork(job);
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (job != null)
                            job.reject(e);
                    } finally {
                        melAuthService.getPowerManager().releaseWakeLock(this);
                    }
                } else {
                    // wait here if no jobs are available
                    waitLock.lock();
                    //Lok.debug(getRunnableName() + "...unlocked");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void workWork(Job job) throws Exception;


    public void addJob(Job job) {
        queueLock.lockWrite();
        jobs.offer(job);
        queueLock.unlockWrite();
        if (isStopped()) {
            Lok.error("DEBUG: JOB ADDED BEFORE STARTED");
            return;
        }
        waitLock.unlock();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        super.onShutDown();
        queueLock.unlockWrite();
        waitLock.unlock();
        return null;
    }

    /**
     * this is method is called when all {@link MelService}s and {@link MelAuthService} booted up.
     * the worker waits for a lock being released when this method is called.
     * this prevents it from trying to communicate while initialization is still in progress and hurts you.
     */
    public void start() {
        initLock.unlock();
        //melAuthService.execute(this);
    }

}
