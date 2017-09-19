package de.mein.auth.tools;

import de.mein.sql.RWLock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntBinaryOperator;

//public class CountLock {
//
//    private AtomicInteger counter = new AtomicInteger(0);
//    private Lock accessLock = new ReentrantLock();
//    private RWLock lock = new RWLock();
//
//    public CountLock lock() {
//        accessLock.lock();
//        if (counter.incrementAndGet() > 1) {
//            lock.lockWrite();
//        }
//        accessLock.unlock();
//        return this;
//    }
//
//    public CountLock unlock() {
//        synchronized (counter) {
//            counter.decrementAndGet();
//            lock.unlockWrite();
//        }
//        return this;
//    }
//}


/**
 * Created by xor on 6/7/17.
 */
public class CountLock {

    private final AtomicInteger counter = new AtomicInteger(0);
    private Lock accessLock = new ReentrantLock();
    private RWLock lock = new RWLock().lockWrite();
    //todo debug
    private Thread lastLockedThread;
    private StackTraceElement[] stacktrace;

    public CountLock lock() {
        accessLock.lock();
//        if (counter.incrementAndGet() > 0) {
//            //todo debug
//            if (counter.get() == 2) {
//                System.out.println("CountLock.lock.debug");
//            }
//            lastLockedThread = Thread.currentThread();
//            try{
//                throw new Exception("");
//            }catch (Exception e){
//                this.stacktrace = e.getStackTrace();
//            }
//            lock.lockWrite();
//        }
        if (counter.accumulateAndGet(1,intBinaryOperator)>0)
            lock.lockWrite();
        accessLock.unlock();
        return this;
    }

    private IntBinaryOperator intBinaryOperator = (left, right) -> {
        if (left + right > 1)
            return 1;
        return left + right;
    };

    public CountLock unlock() {
        synchronized (counter) {
            //todo debug
            if (counter.get() == 2) {
                System.out.println("CountLock.unlock.debug");
            }
//            if (counter.accumulateAndGet(-1, intBinaryOperator) < 1)
//                lock.unlockWrite();
            if (counter.decrementAndGet() < 1)
                lock.unlockWrite();
        }
        return this;
    }

}
