package de.mel.filesync.data.conflict;

import de.mel.filesync.sql.dao.ConflictDao;

/**
 * used to display a gap between root conflicts
 * Created by xor on 13.07.2017.
 */
public class EmptyRowConflict extends Conflict {
    public EmptyRowConflict(ConflictDao conflictDao) {
        super(conflictDao, null, null);
    }
}
