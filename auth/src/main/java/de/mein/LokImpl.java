package de.mein;

public class LokImpl {
    protected boolean printDebug = true;
    protected boolean printError = true;
    protected boolean printWarn = true;
    protected boolean printInfo = true;

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
        return "[" + stackTrace.getMethodName() + "] [" + stackTrace.getLineNumber() + "]." + mode + ": " + (msg == null ? "null" : msg.toString());
    }

    private String fabricate(String mode, Object msg) {
        StackTraceElement stackTrace = Thread.currentThread().getStackTrace()[4];
        String tag = stackTrace.getFileName();
        String line = fabricateLine(stackTrace, mode, msg);
        return tag + "." + line;
    }

    public void debug(Object msg) {
        if (printDebug) {
            String line = fabricate("d", msg);
            System.out.println(line);
        }
    }

    public void error(Object msg) {
        if (printError) {
            String line = fabricate("e", msg);
            System.err.println(line);
        }
    }

    public void warn(Object msg) {
        if (printWarn) {
            String line = fabricate("w", msg);
            System.out.println(line);
        }
    }

    public void info(Object msg) {
        if (printInfo) {
            String line = fabricate("i", msg);
            System.out.println(line);
        }
    }
}
