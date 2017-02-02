package de.mein.auth.service;

import de.mein.MeinRunnable;
import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.sql.RWLock;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MeinServiceWorker extends MeinRunnable implements IMeinService {
    protected final MeinAuthService meinAuthService;
    protected LinkedList<Job> jobs = new LinkedList<>();
    private RWLock listLock = new RWLock().lockWrite();
    private Map<String, MeinIsolatedProcess> isolatedProcessMap = new HashMap<>();

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

    public MeinServiceWorker(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }

    @Override
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public void run() {
        try {
            while (true) {
                while (hasJobs()) {
                    synchronized (jobs) {
                        Job job = jobs.poll();
                        try {
                            workWork(job);
                        } catch (Exception e) {
                            if (job.getPromise() != null)
                                job.getPromise().reject(e);
                        }
                    }
                }
                // wait until jobs have arrived
                listLock.lockWrite();
            }
        } catch (Exception e) {
            boolean b = thread.isInterrupted();
            e.printStackTrace();
        }
    }

    protected abstract void workWork(Job job) throws Exception;

    private boolean hasJobs() {
        boolean result;
        synchronized (jobs) {
            result = jobs.size() > 0;
        }
        return result;
    }

    public void addJob(Job job) {
        synchronized (jobs) {
            boolean changed = jobs.offer(job);
        }
        listLock.unlockWrite();
    }

}
