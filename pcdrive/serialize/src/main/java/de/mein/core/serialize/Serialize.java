package de.mein.core.serialize;

/**
 * Created by xor on 5/18/16.
 */
public class Serialize {
    public static boolean print = false;

    public static void println(Object obj) {
        if (print && obj != null)
            System.out.println(obj.toString());
    }
}
