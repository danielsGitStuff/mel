import android.os.FileObserver;
import android.support.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 31.07.2017.
 */

public class RecursiveWatcher {
    private final File target;
    private final RecursiveWatcher parent;
    private final Map<String, Watcher> watchers = new HashMap<>();

    private RecursiveWatcher(RecursiveWatcher parent, File target) {
        this.target = target;
        this.parent = parent;
        watch(target);
    }

    public static RecursiveWatcher instance(RecursiveWatcher parent, File target) {
        return new RecursiveWatcher(parent, target);
    }

    private class Watcher extends FileObserver {

        private final RecursiveWatcher recursiveWatcher;
        private final File target;

        public Watcher(RecursiveWatcher recursiveWatcher, File target) {
            super(target.getAbsolutePath());
            this.target = target;
            this.recursiveWatcher = recursiveWatcher;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            recursiveWatcher.eve(this, event, path);
        }

        public File getTarget() {
            return target;
        }
    }

    private void watch(File target) {
        if (!watchers.containsKey(target.getAbsolutePath())) {
            Watcher watcher = new Watcher(this, target);
            watchers.put(target.getAbsolutePath(), watcher);
            watcher.startWatching();
        }
    }

    private void eve(Watcher watcher, int event, String path) {
        File f = new File(watcher.getTarget() + File.separator + path);
        if ((FileObserver.CREATE & event) != 0 && f.exists() && f.isDirectory()) {
            watch(f);
        }
    }   
}