package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.auth.jobs.Job;
import de.mein.auth.tools.WaitLock;
import de.mein.sql.RWLock;

import java.util.LinkedList;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MeinWorker extends DeferredRunnable {
    protected LinkedList<Job> jobs = new LinkedList<>();
    protected RWLock queueLock = new RWLock();
    protected WaitLock waitLock = new WaitLock();

    @Override
    public void runImpl() {
        try {
            while (!isInterrupted()) {
                queueLock.lockWrite();
                Job job = jobs.poll();
                queueLock.unlockWrite();
                if (job != null) {
                    try {
                        workWork(job);
                    } catch (Exception e) {
                        e.printStackTrace();/*
                            if (job.getPromise() != null)
                                job.getPromise().reject(e);*/
                    }
                } else {
                    // wait here if no jobs are available
                    waitLock.lockWrite();
                    System.out.println(getRunnableName() + "...unlocked");
                }
            }
            System.out.println(getClass().getSimpleName() + " has finished");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Do your work here. The worker will wait until new jobs have arrived.
     * @param job
     * @throws Exception
     */
    protected abstract void workWork(Job job) throws Exception;


    public void addJob(Job job) {
        queueLock.lockWrite();
        jobs.offer(job);
        queueLock.unlockWrite();
        waitLock.unlockWrite();
    }

    @Override
    public void onShutDown() {
        queueLock.unlockWrite();
        waitLock.unlockWrite();
    }
}
