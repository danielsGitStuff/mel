package de.mel.util;

import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictRow;
import de.mel.filesync.sql.FsDirectory;
import de.mel.filesync.sql.FsEntry;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.dao.ConflictDao;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import org.junit.Before;
import org.junit.Test;

public class ObservableTest {
    ConflictDao conflictDao;

    @Before
    public void before() {
        FsDao fsDao = new FsDao(null, null);
        StageDao stageDao = new StageDao(null, null, fsDao);
        this.conflictDao = new ConflictDao(stageDao, fsDao);
    }

    @Test
    public void change() {
        FsEntry fsRoot = new FsDirectory().setName("[root]").setId(1L).setVersion(1L);
        ConflictRow row = new ConflictRow(fsRoot);

    }

    @Test
    public void change2() {

        Stage localStage = new Stage().setName("local stage");
        Stage remoteStage = new Stage().setName("remote stage");
        Conflict conflict = new Conflict(this.conflictDao, localStage, remoteStage);
        ConflictRow row = new ConflictRow(conflict);
    }
}
