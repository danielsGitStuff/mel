package de.mein;

/**
 * Created by xor on 4/25/17.
 */
public interface MeinRunnable extends Runnable {
    /**
     * will appear as the executing Thread name. Very helpful.
     *
     * @return
     */
    String getRunnableName();

    default void onStart() {
    }
}
