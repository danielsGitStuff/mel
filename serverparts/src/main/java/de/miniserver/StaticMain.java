package de.miniserver;

import de.mein.Lok;
import de.mein.auth.tools.N;

public class StaticMain {
    public static void main(String[] args) {
        N.r(() -> System.out.println("StaticMain.main.start"));
//        StaticServer.main(args);
        Lok.debug("staticserver.main.end");
    }
}
