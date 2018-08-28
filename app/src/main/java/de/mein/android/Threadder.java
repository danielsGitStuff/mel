package de.mein.android;

import de.mein.auth.tools.N;

/**
 * Created by xor on 3/8/17.
 */

public class Threadder {
    public static void runNoTryThread(N.INoTryRunnable noTryRunnable) {
        new Thread(() -> N.r(noTryRunnable)).start();
    }
}
