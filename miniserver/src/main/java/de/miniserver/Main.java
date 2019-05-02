package de.miniserver;

import de.mein.KResult;
import de.mein.konsole.Konsole;

public class Main {
    public static void main(String[] args) throws Konsole.KonsoleWrongArgumentsException, Konsole.HelpException, Konsole.DependenciesViolatedException {
        class Dummy implements KResult {
            String string;
        }
        Dummy dummy = new Dummy();
        Konsole<Dummy> konsole = new Konsole<>(dummy);
        konsole.mandatory("-restart-command", "descr", (result1, args1) -> dummy.string = args1[0]);
        konsole.handle(args);
        System.out.println(dummy.string);
        String[] tokenized = Konsole.tokenizeArgument(dummy.string);
        Processor processor = new Processor(tokenized);
        processor.run(true, ProcessBuilder.Redirect.PIPE, ProcessBuilder.Redirect.PIPE);
    }
}
