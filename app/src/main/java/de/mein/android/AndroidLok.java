package de.mein.android;

import android.util.Log;
import android.util.Pair;

import de.mein.LokImpl;

public class AndroidLok extends LokImpl {

    private Pair<String, String> fabricate(String mode, Object msg) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StackTraceElement stackTrace = stackTraceElements[5];
        String tag = stackTrace.getFileName();
        String line = this.fabricateLine(stackTrace, mode, msg);
        return new Pair<>(tag, line);
    }

    @Override
    public void debug(Object msg) {
        if (printDebug) {
            Pair<String, String> pair = fabricate("d", msg);
            Log.d(pair.first, pair.second);
        }
    }

    @Override
    public void error(Object msg) {
        if (printError) {
            Pair<String, String> pair = fabricate("e", msg);
            Log.e(pair.first, pair.second);
        }
    }

    @Override
    public void warn(Object msg) {
        if (printWarn) {
            Pair<String, String> pair = fabricate("w", msg);
            Log.w(pair.first, pair.second);
        }
    }

    @Override
    public void info(Object msg) {
        if (printInfo) {
            Pair<String, String> pair = fabricate("i", msg);
            Log.i(pair.first, pair.second);
        }
    }


}
