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
    private RWLock listLock = new RWLock().lockWrite();

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
                            e.printStackTrace();/*
                            if (job.getPromise() != null)
                                job.getPromise().reject(e);*/
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
