package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.sql.RWLock;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MeinServiceWorker extends MeinService implements IMeinService {
    protected LinkedList<Job> jobs = new LinkedList<>();
    private RWLock queueLock = new RWLock();
    protected RWLock waitLock = new RWLock();
    private Map<String, MeinIsolatedProcess> isolatedProcessMap = new HashMap<>();

    public MeinServiceWorker(MeinAuthService meinAuthService) {
        super(meinAuthService);
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

    protected String uuid;


    @Override
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public void runImpl() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
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
                    waitLock.lockWrite();
                    System.out.println(getRunnableName() + "...unlocked");
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
        waitLock.unlockWrite();
    }

    public void start() {
        meinAuthService.execute(this);
    }
}
