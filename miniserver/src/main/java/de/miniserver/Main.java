package de.miniserver;

import de.mein.Lok;
import de.mein.auth.tools.N;

public class Main{
    public static void main(String[] args) {
        N.r(() -> System.out.println("Main.main.start"));
        MiniServer.main(args);
        Lok.debug("miniserver.main.end");
    }
}
