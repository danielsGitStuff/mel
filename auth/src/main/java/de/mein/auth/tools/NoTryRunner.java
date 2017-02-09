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

    public interface NoTryExceptionConsumer {
        void accept(Exception e);
    }


    private static NoTryRunner runner = new NoTryRunner(new NoTryExceptionConsumer() {
        @Override
        public void accept(Exception e) {
            e.printStackTrace();
        }
    });

    public static void run(NoTryRunner.INoTryRunnable noTryRunnable) {
        NoTryRunner.runner.runTry(noTryRunnable);
    }

    private NoTryExceptionConsumer consumer;

    public NoTryRunner(NoTryExceptionConsumer consumer) {
        this.consumer = consumer;
    }

    public NoTryExceptionConsumer getConsumer() {
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

    public NoTryRunner runTry(INoTryRunnable noTryRunnable, NoTryExceptionConsumer consumer) {
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

