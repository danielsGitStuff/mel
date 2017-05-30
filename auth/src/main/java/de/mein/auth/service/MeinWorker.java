package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.auth.jobs.Job;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.sql.RWLock;
import org.jdeferred.impl.DeferredObject;

import java.util.LinkedList;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MeinWorker extends DeferredRunnable {
    protected LinkedList<Job> jobs = new LinkedList<>();
    protected RWLock queueLock = new RWLock();
    protected WaitLock waitLock = new WaitLock();
    private DeferredObject workDonePromise;

    @Override
    public void runImpl() {
        try {
            while (!isInterrupted()) {
                queueLock.lockWrite();
                Job job = jobs.poll();
                queueLock.unlockWrite();
                if (job != null) {
                    try {
                        workDonePromise = new DeferredObject();
                        workWork(job);
                        workDonePromise.resolve(null);
                    } catch (Exception e) {
                        e.printStackTrace();/*
                            if (job.getPromise() != null)
                                job.getPromise().reject(e);*/
                    }
                } else {
                    // wait here if no jobs are available
                    if (workDonePromise != null)
                        N.s(() -> workDonePromise.resolve(null));
                    N.r(() -> waitLock.lock());
                    System.out.println(getRunnableName() + "...unlocked");
                }
            }
            if (workDonePromise != null) {
                RWLock shutDownLock = new RWLock().lockWrite();
                workDonePromise.done(result -> shutDownLock.unlockWrite());
                shutDownLock.lockWrite();
                System.out.println("MeinWorker.runImpl.work done. shutting down");
            }
            System.out.println(getClass().getSimpleName() + " has finished");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Do your work here. The worker will wait until new jobs have arrived.
     *
     * @param job
     * @throws Exception
     */
    protected abstract void workWork(Job job) throws Exception;


    public void addJob(Job job) {
        if (!isInterrupted()) {
            queueLock.lockWrite();
            jobs.offer(job);
            queueLock.unlockWrite();
            waitLock.unlock();
        }
    }

    @Override
    public void onShutDown() {
        queueLock.unlockWrite();
        waitLock.unlock();
    }
}
