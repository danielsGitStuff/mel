package de.mein;

import java.util.Random;

/**
 * Created by xor on 05.09.2016.
 */
public class MeinThread extends Thread {

    public MeinThread(Runnable runnable) {
        super(runnable);
        setName(runnable.getClass().getSimpleName() + ".id:" + new Random().nextInt(1000));
    }

    @Override
    public void interrupt() {
        super.interrupt();
    }

}
