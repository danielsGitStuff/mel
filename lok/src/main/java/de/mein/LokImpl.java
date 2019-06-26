package de.mein;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class LokImpl {

    private boolean timeStamp = true;
    private String[] lines = new String[0];
    private long lineCount = 0;
    private int pos = 0;
    private boolean filled = false;
    private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private ReentrantLock reentrantLock = new ReentrantLock(true);
    protected boolean printDebug = true;
    protected boolean printError = true;
    protected boolean printWarn = true;
    protected boolean printInfo = true;
    protected int stackIndex = 4;
    protected String devMatchLine;
    protected Runnable devMatchRunnable;
    private LokListener lokListener;

    public void setLokListener(LokListener lokListener) {
        this.lokListener = lokListener;
    }

    public LokImpl setup(int lines, boolean timeStamp) {
        reentrantLock.lock();
        this.lines = new String[lines];
        this.timeStamp = timeStamp;
        this.pos = 0;
        this.filled = false;
        reentrantLock.unlock();
        return this;
    }

    public boolean isLineStorageActive() {
        return lines.length > 0;
    }

    public void devOnLineMatches(String line, Runnable r) {
        devMatchLine = line;
        devMatchRunnable = r;
    }

    public interface LokListener {
        void onPrintLn(String line);
    }

    public LokImpl setPrintDebug(boolean printDebug) {
        this.printDebug = printDebug;
        return this;
    }

    public LokImpl setPrintError(boolean printError) {
        this.printError = printError;
        return this;
    }

    public LokImpl setPrintInfo(boolean printInfo) {
        this.printInfo = printInfo;
        return this;
    }

    public LokImpl setPrintWarn(boolean printWarn) {
        this.printWarn = printWarn;
        return this;
    }

    protected String fabricateLine(StackTraceElement stackTrace, String mode, Object msg) {
        return "[" + stackTrace.getMethodName() + "][" + stackTrace.getLineNumber() + "].[" + mode + "] = " + (msg == null ? "null" : msg.toString());
    }

    protected synchronized String fabricate(StackTraceElement stackTrace, String mode, Object msg, boolean insertTag) {
        String tag = stackTrace.getFileName();
        String fabricateLine = fabricateLine(stackTrace, mode, msg);

//        reentrantLock.lock();
        lineCount++;
        Date date = new Date();
        String line = "";
        if (timeStamp) {
            line += "[" + dateFormat.format(date) + "]";
        }
        line += "[" + lineCount + "]";
        if (insertTag)
            line += "[" + tag + "]";
        line += fabricateLine;
        if (lines.length > pos) {
            lines[pos] = line;
            pos++;
        }
        if (pos >= lines.length) {
            pos = 0;
            filled = true;
        }
        if (lokListener != null)
            lokListener.onPrintLn(line);
//        reentrantLock.unlock();
        return line;
    }


    public void debug(Object msg) {
        if (printDebug) {
            String line = fabricate(findStackElement(), "d", msg, true);
            System.out.println(line);
        }
        if (devMatchLine!= null && devMatchLine.equals(msg))
            devMatchRunnable.run();
    }

    protected StackTraceElement findStackElement() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        return elements[this.stackIndex];
    }

    public void error(Object msg) {
        if (printError) {
            String line = fabricate(findStackElement(), "e", msg, true);
            System.err.println(line);
        }
    }

    public void warn(Object msg) {
        if (printWarn) {
            String line = fabricate(findStackElement(), "w", msg, true);
            System.out.println(line);
        }
    }

    public void info(Object msg) {
        if (printInfo) {
            String line = fabricate(findStackElement(), "i", msg, true);
            System.out.println(line);
        }
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
}
