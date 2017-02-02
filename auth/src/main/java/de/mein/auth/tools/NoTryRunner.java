package de.mein.auth.tools;

import de.mein.auth.socket.ShamefulSelfConnectException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by xor on 08.05.2016.
 */
public class NoTryRunner {
    public interface INoTryRunnable {
        void run() throws Exception, ShamefulSelfConnectException;
    }

    private static NoTryRunner runner = new NoTryRunner(Throwable::printStackTrace);

    public static void run(NoTryRunner.INoTryRunnable noTryRunnable) {
        NoTryRunner.runner.runTry(noTryRunnable);
    }

    private Consumer<Exception> consumer;

    public NoTryRunner(Consumer<Exception> consumer) {
        this.consumer = consumer;
    }

    public Consumer<Exception> getConsumer() {
        return consumer;
    }

    public NoTryRunner runTry(INoTryRunnable noTryRunnable) {
        try {
            noTryRunnable.run();
        } catch (Exception e) {
            consumer.accept(e);
        }
        return this;
    }

    public NoTryRunner runTry(INoTryRunnable noTryRunnable, Consumer<Exception> consumer) {
        try {
            noTryRunnable.run();
        } catch (Exception e) {
            consumer.accept(e);
        }
        return this;
    }

    public static void main(String[] args) {
        NoTryRunner runner = new NoTryRunner(e -> System.out.println("NoTryRunner.main." + e.getMessage()));
        runner.runTry(() -> {
            List<String> list = (ArrayList) ((Object) 12);
            System.out.println(list);
        });
        System.out.println("NoTryRunner.main.end");
    }
}

