package de.mel.testing;

public class TestThread {
    private final TestRunnable runnable;
    private Thread thread;
    private String name = "no name";
    private boolean crashed, successful = false;

    public TestThread(TestRunnable runnable) {
        this.runnable = runnable;
    }

    public void start() {
        Runnable wrapper = () -> {
            try {
                this.runnable.run();
                this.successful = true;
            } catch (Exception e) {
                e.printStackTrace();
                this.crashed = true;
            }
        };
        thread = new Thread(wrapper);
        thread.setName(name);
        thread.start();
    }

    public boolean isCrashed() {
        return crashed;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void interrupt() {
        this.thread.interrupt();
        this.thread.stop();
    }
}
