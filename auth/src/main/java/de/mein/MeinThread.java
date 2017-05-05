package de.mein;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Created by xor on 05.09.2016.
 */
public class MeinThread extends Thread {

    public interface Interruptable {


    }

    public MeinThread(){

    }

    public MeinThread(MeinRunnable runnable) {
        super(runnable);

        if (runnable instanceof Interruptable) {
//            Interruptable interruptable = (Interruptable) runnable;
//            interruptable.setMeinThread(this);
        }
        setName(runnable.getRunnableName() + ".id:" + new Random().nextInt(1000));
    }

    @Override
    public String toString() {
        return "Thr." + getName();
    }

    @Override
    public void interrupt() {
        super.interrupt();
    }
}
