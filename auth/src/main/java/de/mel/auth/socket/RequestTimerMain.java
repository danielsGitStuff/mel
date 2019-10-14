package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.data.MelRequest;
import de.mel.auth.socket.process.val.Request;
import de.mel.sql.RWLock;

/**
 * this is only here because intellij cannot find a kotlin main function
 */
public class RequestTimerMain {
    public static void main(String[] args) throws InterruptedException {
        RequestTimer.main(args);

//        MelRequest request = new MelRequest("test", "intent");
//        request.getAnswerDeferred().done(t -> {
//            Lok.debug("done");
//        }).fail(e -> {
//            Lok.debug("failed successfully");
//        });
//
//        RequestTimer timer = new RequestTimer(request);
//        timer.doTimerThings();
//
//        Thread.sleep(2500L);
//        timer.restart();
//        Lok.debug("main done");
    }
}
