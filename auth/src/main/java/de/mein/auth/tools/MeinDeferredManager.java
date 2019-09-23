package de.mein.auth.tools;

import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;
import org.jdeferred.multiple.MasterProgress;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;

import java.util.Collection;

/**
 * resolves if when() is called with a zero sized array
 * Created by xor on 5/21/17.
 */
public class MeinDeferredManager extends DefaultDeferredManager {

    @Override
    public Promise<MultipleResults, OneReject, MasterProgress> when(Promise... promises) {
        if (promises.length == 0) {
            DeferredObject<MultipleResults, OneReject, MasterProgress> result = new DeferredObject<>();
            result.resolve(null);
            return result;
        } else
            return super.when(promises);
    }

    public Promise<MultipleResults, OneReject, MasterProgress> when(Collection<Promise> promises) {
        return when(promises.toArray(new Promise[0]));
    }
}
