package de.mein.konsole;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 3/12/17.
 */
public class Konsole {
    private Map<String, KonsoleArgumentDefinition> argsMap = new HashMap<>();
    private Map<String, String> descMap = new HashMap<>();
    private int pos = 0;

    public Konsole addDefinition(String name, KonsoleArgumentDefinition definition) {
        argsMap.put(name, definition);
        return this;
    }

    public Konsole addDefinition(String name, String description, KonsoleArgumentDefinition definition) {
        argsMap.put(name, definition);
        descMap.put(name, description);
        return this;
    }

    public Konsole handle(String[] args) throws KonsoleWrongArgumentsException {
        while (pos < args.length) {
            String name = args[pos];
            KonsoleArgumentDefinition definition = argsMap.get(name);
            if (definition == null)
                throw new KonsoleWrongArgumentsException("unknown argument: " + name);
            try {
                pos++;
                pos += definition.handleArgs(args, pos);
            } catch (Exception e) {
                throw new KonsoleWrongArgumentsException(e.getMessage());
            }
        }
        return this;
    }
}
