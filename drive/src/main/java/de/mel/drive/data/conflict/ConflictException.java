package de.mel.drive.data.conflict;

/**
 * Created by xor on 7/4/17.
 */
public abstract class ConflictException extends Exception{

    public static class UnsolvedConflictsException extends ConflictException {
    }

    public static class ContradictingConflictsException extends ConflictException {
    }
}
