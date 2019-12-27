package de.mel.filesync.data.conflict;

/**
 * Created by xor on 7/4/17.
 */
public class ConflictException extends Exception {

    public ConflictException(String msg) {
        super(msg);
    }

    public static class UnsolvedConflictsException extends ConflictException {
        public UnsolvedConflictsException(String msg) {
            super(msg);
        }
    }

    public static class ContradictingConflictsException extends ConflictException {
        public ContradictingConflictsException(String msg) {
            super(msg);
        }
    }
}
