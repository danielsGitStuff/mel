package de.mein.auth.gui;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Attempt to fox the following error message:<br>
 * <br>
 * [xcb] Unknown sequence number while processing queue<br>
 * [xcb] Most likely this is a multi-threaded client and XInitThreads has not been called<br>
 * [xcb] Aborting, sorry about that.<br>
 * <br>
 * This crashes the whole application without any possibility handle that exception.
 * After reading the internet I assume this has something to do with concurrent threads which do GUI stuff simultaneously.
 * AFAIK this is a bug in the JDK but people rarely stumble upon it, others said RTFM. However, I did not find da solution.
 */
public class XCBFix {
    private static ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("XCBFix-Gui-Tread");
        return t;
    });

    public static void runLater(Runnable r) {
        Runnable wrapper = () -> Platform.runLater(r);
        executor.submit(wrapper);
    }
}
