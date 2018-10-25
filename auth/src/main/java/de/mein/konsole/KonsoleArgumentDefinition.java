package de.mein.konsole;

/**
 * Created by xor on 3/12/17.
 */
public interface KonsoleArgumentDefinition {


    /**
     * @param args
     * @param pos  points at the first argument you should to consume<br>
     *             eg: "-t 6 8" -> args[pos]=="6"
     * @return how many args have been "used"
     * @throws KonsoleWrongArgumentsException
     */
    int handleArgs(String[] args, int pos) throws KonsoleWrongArgumentsException;
}
