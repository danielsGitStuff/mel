package de.mein.auth.tools;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * logs stuff and has a simple on/off switch
 * Created by xor on 1/15/17.
 */
public class Lok extends PrintStream {

    private boolean print = true;

    public Lok setPrint(boolean print) {
        this.print = print;
        return this;
    }

    public boolean getPrint() {
        return print;
    }

    public Lok(OutputStream out) {
        super(out);
    }

    public Lok(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
    }


    @Override
    public void println(Object x) {
        if (print)
            super.println(x);
    }

    @Override
    public void println(String x) {
        if (print)
            super.println(x);
    }
}
