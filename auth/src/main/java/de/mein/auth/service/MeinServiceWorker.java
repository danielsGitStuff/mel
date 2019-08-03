package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.tools.CountdownLock;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MeinServiceWorker extends MeinService {
    protected LinkedList<Job> jobs = new LinkedList<>();
    private CountdownLock initLock = new CountdownLock(1);

    public MeinServiceWorker(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid, Bootloader.BootLevel bootLevel) {
        super(meinAuthService, workingDirectory, serviceTypeId, uuid, bootLevel);
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
                        meinAuthService.getPowerManager().wakeLock(this);
                        workWork(job);
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (job.getPromise() != null)
                            job.getPromise().reject(e);
                    } finally {
                        meinAuthService.getPowerManager().releaseWakeLock(this);
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
    public void onShutDown() {
        super.onShutDown();
        queueLock.unlockWrite();
        waitLock.unlock();
    }

    /**
     * this is method is called when all {@link MeinService}s and {@link MeinAuthService} booted up.
     * the worker waits for a lock being released when this method is called.
     * this prevents it from trying to communicate while initialization is still in progress and hurts you.
     */
    public void start() {
        initLock.unlock();
        //meinAuthService.execute(this);
    }

}
