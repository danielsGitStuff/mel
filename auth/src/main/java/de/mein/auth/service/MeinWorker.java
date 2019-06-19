package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.jobs.Job;
import de.mein.auth.tools.CountLock;
import de.mein.auth.tools.N;
import de.mein.sql.RWLock;

import org.jdeferred.impl.DeferredObject;

import java.util.LinkedList;

/**
 * Default worker class. Queues jobs and has an own working thread.
 * The instance might be suspended indirectly by the {@link de.mein.auth.service.power.PowerManager}.
 * If you listen for system events like listening for file system changes
 * you should not create a new Job or trigger timers every time you get an event since this would only fill the job queue.
 * Instead take note somewhere, and only add one job.
 * <p>
 * Created by xor on 9/25/16.
 */
public abstract class MeinWorker extends DeferredRunnable {
    protected LinkedList<Job> jobs = new LinkedList<>();
    protected RWLock queueLock = new RWLock();
    protected CountLock waitLock = new CountLock();
    private DeferredObject workDonePromise;

    @Override
    public void runImpl() {
        try {
            while (!isStopped()) {
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
                    //Lok.debug(getRunnableName() + "...unlocked");
                }
            }
            if (workDonePromise != null) {
                RWLock shutDownLock = new RWLock().lockWrite();
                workDonePromise.done(result -> shutDownLock.unlockWrite());
                shutDownLock.lockWrite();
                Lok.debug("MeinWorker.runImpl.work done. shutting down");
            }
            Lok.debug(getClass().getSimpleName() + " has finished");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Do your work here. The worker will wait until new jobs have arrived.
     *
     * @param job
     */
    protected abstract void workWork(Job job) throws Exception;


    public void addJob(Job job) {
        if (!isStopped()) {
            queueLock.lockWrite();
            jobs.offer(job);
            queueLock.unlockWrite();
            waitLock.unlock();
        }
    }

    /**
     * stops the current worker.
     */
    @Override
    public void onShutDown() {
        queueLock.unlockWrite();
        waitLock.unlock();
    }

    public void stop() {
        queueLock.unlockWrite();
        super.stop();
        waitLock.unlock();
    }
}
