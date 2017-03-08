package de.mein.android;

import de.mein.auth.tools.NoTryRunner;

/**
 * Created by xor on 3/8/17.
 */

public class Threadder {
    public static void runNoTryThread(NoTryRunner.INoTryRunnable noTryRunnable) {
        new Thread(() -> NoTryRunner.run(noTryRunnable)).start();
    }
}
