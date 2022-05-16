package de.mel.filesync.data.conflict;

import de.mel.filesync.sql.FsEntry;
import de.mel.filesync.sql.Stage;

import java.util.HashSet;
import java.util.Set;

/**
 * Wraps a conflict of FsEntry such that you can use it to fill the conflict solving table. Comes with ObservableValues to be used in JavaFX.
 */
public class ConflictRow {
    public static String NAME_EMPTY = "--";

    private Stage stage;
    private Conflict conflict;
    private FsEntry fsEntry;
    private Set<ConflictRow> children = new HashSet<>();

    public ConflictRow(Conflict conflict) {
        this.conflict = conflict;
    }

    public ConflictRow(FsEntry fsEntry) {
        this.fsEntry = fsEntry;
    }

    public ConflictRow(Stage stage) {
        this.stage = stage;
    }


    public void addChild(ConflictRow child) {
        this.children.add(child);
    }

    public String getLocalName() {
        if (this.fsEntry != null) {
            return this.fsEntry.getName().v();
        }
        if (this.stage != null)
            return this.stage.getName();
        if (this.conflict.hasLocalStage()) {
            return this.conflict.getLocalStage().getName();
        }
        return ConflictRow.NAME_EMPTY;
    }

    public String getRemoteName() {
        if (this.fsEntry != null) {
            return this.fsEntry.getName().v();
        }
        if (this.stage != null)
            return this.stage.getName();
        if (this.conflict.hasRemoteStage()) {
            return this.conflict.getRemoteStage().getName();
        }
        return ConflictRow.NAME_EMPTY;
    }

    public boolean hasChoice() {
        return this.fsEntry == null && this.conflict.getHasChoice();
    }

    public boolean hasChosenLocal() {
        return this.conflict != null && this.conflict.getChosenLocal();
    }

    public boolean hasChosenRemote() {
        return this.conflict != null && this.conflict.getChosenRemote();
    }

    public void decideLocal() {
        if (this.conflict != null)
            this.conflict.decideLocal();
        for (ConflictRow child : this.children) {
            child.decideLocal();
        }
    }

    public void decideRemote() {
        if (this.conflict != null)
            this.conflict.decideRemote();
        for (ConflictRow child : this.children) {
            child.decideRemote();
        }
    }

    public Conflict getConflict() {
        return conflict;
    }

    public boolean hasFsEntry() {
        return this.fsEntry != null;
    }

    public FsEntry getFsEntry() {
        return fsEntry;
    }

    public boolean hasConflict() {
        return this.conflict != null;
    }

    public boolean hasStage() {
        return this.stage != null;
    }

    public Stage getStage() {
        return stage;
    }

    public Set<ConflictRow> getChildren() {
        return children;
    }
}
