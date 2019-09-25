package de.mel.auth.tools;

import org.jdeferred.DeferredManager;
import org.jdeferred.DoneCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;

import java.util.ArrayList;

public class ShutDownDeferredManager {
    private DeferredManager manager = new DefaultDeferredManager();
    private ArrayList<Promise> promiseList = new ArrayList<>();
    private DeferredObject<Void, Void, Void> deferred = new DeferredObject<>();

    public ShutDownDeferredManager when(Promise... promises) {
        N.forEach(promises, promise -> {
            if (promise != null)
                promiseList.add(promise);
        });
        return this;
    }

    public DeferredObject<Void, Void, Void> digest() {
        // resolve() under any circumstances. "Nach mir die Sintflut!"
        if (promiseList.size() > 0) {
            manager.when(promiseList.toArray(Promise[]::new)).done(result -> deferred.resolve(null)).fail(result -> deferred.resolve(null));
        } else {
            deferred.resolve(null);
        }
        return deferred;
    }
}
