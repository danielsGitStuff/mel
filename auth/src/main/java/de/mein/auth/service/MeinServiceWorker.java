package de.mein.auth.service;

import de.mein.auth.file.AFile;
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
    private Map<String, MeinIsolatedProcess> isolatedProcessMap = new HashMap<>();
    private CountdownLock initLock = new CountdownLock(1).lock();

    public MeinServiceWorker(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid) {
        super(meinAuthService, workingDirectory, serviceTypeId, uuid);
    }

    @Override
    public void onIsolatedConnectionEstablished(MeinIsolatedProcess isolatedProcess) {
        String key = isolatedProcess.getPartnerCertificateId() + "." + isolatedProcess.getPartnerServiceUuid();
        isolatedProcessMap.put(key, isolatedProcess);
    }

    public MeinIsolatedProcess getIsolatedProcess(Long partnerCertId, String partnerServiceUuid) {
        String key = partnerCertId + "." + partnerServiceUuid;
        return isolatedProcessMap.get(key);
    }

    @Override
    public void runImpl() {
        try {
            initLock.lock();
            while (!isInterrupted()) {
                queueLock.lockWrite();
                Job job = jobs.poll();
                queueLock.unlockWrite();
                if (job != null) {
                    try {
                        workWork(job);
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (job.getPromise() != null)
                            job.getPromise().reject(e);
                    }
                } else {
                    // wait here if no jobs are available
                    waitLock.lock();
                    //System.out.println(getRunnableName() + "...unlocked");
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
