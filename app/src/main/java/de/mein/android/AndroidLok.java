package de.mein.android;

import android.util.Log;
import android.util.Pair;

import de.mein.LokImpl;

public class AndroidLok extends LokImpl {

    public AndroidLok() {
        this.stackIndex = 6;
    }

    private Pair<String, String> fabricatePair(String mode, Object msg) {
        StackTraceElement stackTrace = findStackElement();
        String tag = stackTrace.getFileName();
        String line = super.fabricate(findStackElement(), mode, msg, false);
        return new Pair<>(tag, line);
    }

    @Override
    public void debug(Object msg) {
        if (printDebug) {
            Pair<String, String> pair = fabricatePair("d", msg);
            Log.d(pair.first, pair.second);
        }
    }

    @Override
    public void error(Object msg) {
        if (printError) {
            Pair<String, String> pair = fabricatePair("e", msg);
            Log.e(pair.first, pair.second);
        }
    }

    @Override
    public void warn(Object msg) {
        if (printWarn) {
            Pair<String, String> pair = fabricatePair("w", msg);
            Log.w(pair.first, pair.second);
        }
    }

    @Override
    public void info(Object msg) {
        if (printInfo) {
            Pair<String, String> pair = fabricatePair("i", msg);
            Log.i(pair.first, pair.second);
        }
    }


}
