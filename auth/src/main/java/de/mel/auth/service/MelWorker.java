package de.mel.auth.service;

import de.mel.DeferredRunnable;
import de.mel.auth.jobs.Job;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.N;
import de.mel.sql.RWLock;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.util.LinkedList;

/**
 * Default worker class. Queues jobs and has an own working thread.
 * The instance might be suspended indirectly by the {@link de.mel.auth.service.power.PowerManager}.
 * If you listen for system events like listening for file system changes
 * you should not create a new Job or trigger timers every time you get an event since this would only fill the job queue.
 * Instead take note somewhere, and only add one job.
 * <p>
 * Created by xor on 9/25/16.
 */
public abstract class MelWorker extends DeferredRunnable {
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
                        e.printStackTrace();
                    }
                } else {
                    // wait here if no jobs are available
                    if (workDonePromise != null)
                        N.s(() -> workDonePromise.resolve(null));
                    N.r(() -> waitLock.lock());
                }
            }
            if (workDonePromise != null) {
                RWLock shutDownLock = new RWLock().lockWrite();
                workDonePromise.done(result -> shutDownLock.unlockWrite());
                shutDownLock.lockWrite();
            }
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
    public Promise<Void, Void, Void> onShutDown() {
        stop();
        return DeferredRunnable.ResolvedDeferredObject();
    }

    public void stop() {
        super.stop();
        waitLock.unlock();
    }
}
