package de.mein.auth.tools;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 03.09.2016.
 */
public class WatchDogTimer extends Timer {
    private final WatchDogTimerFinished watchDogTimerFinished;
    private final int repetitions;
    private final int delay;
    private final int period;
    private Timer timer = new Timer("WatchDogTimer");
    private WatchDogTimerTask task;
    private boolean runs = false;
    private Semaphore lock = new Semaphore(1,true);
    private int waitCounter = 0;

    /**
     *
     * @param watchDogTimerFinished
     * @param repetitions repeat n times
     * @param delay starts after n ms
     * @param period wait for n ms after each period
     */
    public WatchDogTimer(WatchDogTimerFinished watchDogTimerFinished, int repetitions, int delay, int period) {
        this.watchDogTimerFinished = watchDogTimerFinished;
        this.repetitions = repetitions;
        this.delay = delay;
        this.period = period;
    }

    /**
     * Starts the Timer if not running. Otherwise it will be reset.
     * @return
     * @throws InterruptedException
     */
    public WatchDogTimer start() throws InterruptedException {
        lock.acquire();
        if (task != null)
            task.reset();
        if (!runs) {
            //System.out.println("WatchDogTimer.start.NEWTASK");
            task = new WatchDogTimerTask(() -> {
                System.out.println("WatchDogTimer.STOPPPPED");
                lock.acquire();
                WatchDogTimer.this.runs = false;
                lock.release();
                watchDogTimerFinished.onTimerStopped();
            }, repetitions);
            runs = true;
            timer.purge();
            timer.scheduleAtFixedRate(task, delay, period);
        }
        lock.release();
        return this;
    }

    public void resume() throws InterruptedException {
        lock.acquire();
        waitCounter--;
        if (waitCounter == 0)
            task.resume();
        lock.release();
    }

    public void waite() throws InterruptedException {
        lock.acquire();
        waitCounter++;
        task.waite();
        lock.release();
    }

    /**
     * calls WatchDogTimerFinished.onTimerStopped() without countdown
     */
    public void finish() throws InterruptedException {
        watchDogTimerFinished.onTimerStopped();
    }

    public interface WatchDogTimerFinished {
        void onTimerStopped() throws InterruptedException;
    }

    /**
     * Created by xor on 03.09.2016.
     */
    static class WatchDogTimerTask extends TimerTask {
        private WatchDogTimerFinished watchDogTimerFinished;

        public WatchDogTimerTask(WatchDogTimerFinished watchDogTimerFinished, int startValue) {
            this.startValue = startValue;
            this.watchDogTimerFinished = watchDogTimerFinished;
        }

        private int startValue = 20;
        private AtomicInteger count = new AtomicInteger(20);
        private AtomicBoolean wait = new AtomicBoolean(false);

        void waite() {
            wait.set(true);
        }

        void resume() {
            wait.set(false);
        }

        @Override
        public void run() {
            int count = this.count.decrementAndGet();
            boolean wait = this.wait.get();
            if (wait) {
                //System.out.println("WatchDogTimerTask.runTry.wait");
                reset();
            } else {
                //System.out.println("WatchDogTimerTask.runTry." + count);
                if (count == 0) {
                    this.cancel();
                    try {
                        watchDogTimerFinished.onTimerStopped();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void reset() {
            //System.out.println("WatchDogTimerTask.reset");
            count.set(startValue);
        }
    }
}
