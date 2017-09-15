package de.mein.auth.tools;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xor on 9/9/17.
 */

public class MeinLogger extends PrintStream {
    private static MeinLogger logger;
    private final boolean timeStamp;
    private boolean filled = false;
    private final String[] lines;
    private long lineCount = 0;
    private int pos = 0;
    private static LoggerListener loggerListener;
    private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private ReentrantLock reentrantLock = new ReentrantLock(true);


    public interface LoggerListener {
        void onPrintLn(String line);
    }

    public MeinLogger(int size, OutputStream out, boolean timeStamp) {
        super(out);
        this.timeStamp = timeStamp;
        lines = new String[size];
    }

    public static MeinLogger getInstance() {
        return logger;
    }

    public static void setLoggerListener(LoggerListener loggerListener) {
        MeinLogger.loggerListener = loggerListener;
    }

    @Override
    public synchronized void println(String line) {
        if (!timeStamp)
            super.println(line);
        if (reentrantLock.hasQueuedThreads())
            lineCount = lineCount;
        reentrantLock.lock();
        lineCount++;
        Date date = new Date();
        line = "[" + dateFormat.format(date) + " | " + lineCount + " ] " + line;
        lines[pos] = line;
        if (timeStamp)
            super.println(line);
        pos++;
        if (pos >= lines.length) {
            pos = 0;
            filled = true;
        }
        if (loggerListener != null)
            loggerListener.onPrintLn(line);
        reentrantLock.unlock();
        //todo debug
        if (lineCount == 776)
            lineCount = lineCount;
    }

    @Override
    public void println(int x) {
        println(((Integer) x).toString());
    }

    @Override
    public void println(char x) {
        println(((Character) x).toString());
    }

    @Override
    public void println(boolean x) {
        println(((Boolean) x).toString());
    }

    @Override
    public void println(long x) {
        println(((Long) x).toString());
    }

    @Override
    public void println(float x) {
        println(((Float) x).toString());
    }

    @Override
    public void println(double x) {
        println(((Double) x).toString());
    }

    @Override
    public void println(char[] x) {
        println(new String(x));
    }

    @Override
    public void println(Object x) {
        if (x != null)
            println(x.toString());
        else
            println("null");
    }

    public String[] getLines() {
        reentrantLock.lock();
        String[] result;
        if (filled) {
            result = new String[lines.length];
        } else {
            result = new String[pos];
        }
        int progress = 0;
        int pos = this.pos;
        for (; pos < result.length; pos++) {
            result[progress++] = lines[pos];
        }
        if (progress < result.length) {
            pos = 0;
            int end = result.length - progress;
            for (; pos < end; pos++) {
                result[progress++] = lines[pos];
            }
        }
        reentrantLock.unlock();
        return result;
    }

    public static void redirectSysOut(int size) {
        redirectSysOut(size, false);
    }

    public static void redirectSysOut(int size, boolean timeStamp) {
        if (logger == null)
            logger = new MeinLogger(size, System.out, timeStamp);
        System.setOut(logger);
        System.setErr(logger);
    }

    public void toSysOut(String line) {
        super.println(line);
    }
}
