package de.mein.auth.service;

import de.mein.MeinRunnable;
import de.mein.auth.jobs.Job;
import de.mein.sql.RWLock;

import java.util.LinkedList;

/**
 * Created by xor on 9/25/16.
 */
public abstract class MeinWorker extends MeinRunnable {
    protected LinkedList<Job> jobs = new LinkedList<>();
    protected RWLock queueLock = new RWLock();
    protected RWLock waitLock = new RWLock();

    @Override
    public void run() {
        try {
            while (true) {
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
                    System.out.println(thread.getName() + "...unlocked");
                }
            }
        } catch (Exception e) {
            boolean b = thread.isInterrupted();
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
}
