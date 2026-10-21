package de.mel.auth.tools;

import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

/**
 * Small helpers around {@link Promise} that Mel needs more than once.
 */
public final class Promises {
    private Promises() {
    }

    /**
     * Resolves once all promises are settled, whatever the outcome ("Nach mir die Sintflut"). Null promises are skipped, no promises resolve at once.
     * That is what shutting down several components needs: one that fails or that has nothing to wait for (null) does not keep the others from finishing.
     */
    public static Promise<Void, Void> allSettled(Promise<?, ?>... promises) {
        PromiseImpl<Void, Void> result = PromiseImpl.withoutThrowingOnException();
        Promise.fused(promises).onDone(settled -> result.resolveOk(null));
        return result;
    }
}
