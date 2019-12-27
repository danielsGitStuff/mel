package de.mel.util;

import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.sql.Stage;
import de.mel.sql.SqlQueriesException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

import static org.junit.Assert.*;

/**
 * find.. methods create conflicts in the stage sets and must find them or must not find them.
 * solve.. methods check whether the applied solutions are valid.
 * <p>
 * naming scheme is as follows:
 * <p>
 * findX ... let the ConflictSolver find X
 * mergeX ... let the ConflictSolver find X, appply solution and test the merged StageSet
 * solve whatHappensBeforeConflictSearching Side
 * <p>
 * Side = Local | Remote | Mixed
 * whatHappensBeforeConflictSearching = DeleteX | ModifyX ...
 */
public class ConflictTest extends MergeTest {


    ConflictSolver conflictSolver;


    /**
     * this creates two equal stage sets.
     *
     * @throws SqlQueriesException
     * @throws IOException
     * @throws SQLException
     */
    @Before
    public void before() throws SqlQueriesException, IOException, SQLException, InterruptedException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException, JsonSerializationException, JsonDeserializationException {
        super.before();
    }

    /**
     * delete /a/aa.txt remotely
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findDeletedFileConflict() throws SqlQueriesException {
        Stage aatxt = creationRemoteDao.get("aa.txt");
        aatxt.setDeleted(true);
        stageDao.update(aatxt);

        createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getConflictMap().size());
        assertEquals(1, conflictSolver.getLocalStageConflictMap().size());
        assertEquals(1, conflictSolver.getRemoteStageConflictMap().size());
    }

    /**
     * cofnlict in /a/aa.txt
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findContentFileConflict() throws SqlQueriesException {
        Stage aatxt = creationRemoteDao.get("aa.txt");
        aatxt.setContentHash("changed");
        stageDao.update(aatxt);

        createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getConflictMap().size());
        assertEquals(1, conflictSolver.getLocalStageConflictMap().size());
        assertEquals(1, conflictSolver.getRemoteStageConflictMap().size());
    }

    private ConflictSolver createConflictSolver() {
        conflictSolver = new ConflictSolver(conflictDao, localStageSet, remoteStageSet);
        return conflictSolver;
    }

    /**
     * delete /b/bb/ remotely
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findRemoteDeletedFolder() throws SqlQueriesException {
        Stage bbbtext = creationRemoteDao.get("bbb.txt");
        stageDao.deleteStageById(bbbtext.getId());
        Stage bb = creationRemoteDao.get("bb");
        bb.setDeleted(true);
        stageDao.update(bb);

        createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getRootConflictMap().size());
        assertEquals(2, conflictSolver.getConflictMap().size());
    }

    /**
     * test whether the decision is properly propagated
     *
     * @throws SqlQueriesException
     */
    @Test
    public void solveContentFileConflictLocal() throws SqlQueriesException {
        findContentFileConflict();
        Conflict localConflict = conflictSolver.getLocalStageConflictMap().values().iterator().next();
        Conflict remoteConflict = conflictSolver.getRemoteStageConflictMap().values().iterator().next();
        assertFalse(localConflict.getHasChoice());
        assertFalse(localConflict.getHasChoice());
        localConflict.decideLocal();
        assertTrue(localConflict.getHasChoice());
        assertTrue(remoteConflict.getHasChoice());
        assertTrue(localConflict.getChosenLocal());
        assertFalse(localConflict.getChosenRemote());
        assertTrue(remoteConflict.getChosenLocal());
        assertFalse(remoteConflict.getChosenRemote());
    }

    @Test
    public void solveContentFileConflictRemote() throws SqlQueriesException {
        findContentFileConflict();
        conflictSolver.getRemoteStageConflictMap().values().forEach(Conflict::decideRemote);
        assertFalse(conflictSolver.hasConflicts());
        conflictSolver.merge();
    }

    @Test
    public void solveDeleteRemoteParentLocal() throws SqlQueriesException {
        {
            Stage bbbtext = creationRemoteDao.get("bbb.txt");
            stageDao.deleteStageById(bbbtext.getId());
            Stage bb = creationRemoteDao.get("bb");
            stageDao.deleteStageById(bb.getId());
            Stage b = creationRemoteDao.get("b");
            b.setDeleted(true);
            stageDao.update(b);
        }
        createConflictSolver().findConflicts();
        assertEquals(3, conflictSolver.getConflictMap().size());

        Conflict bRemote = conflictSolver.getLocalStageConflictMap().values().stream().filter(conflict -> conflict.getRemoteStage().getNamePair().equalsValue("b")).findFirst().get();
        bRemote.decideLocal();

        conflictSolver.getLocalStageConflictMap().values().forEach(conflict ->
        {
            assertTrue(conflict.getHasChoice());
            assertTrue(conflict.getChosenLocal());
        });
        conflictSolver.getRemoteStageConflictMap().values().forEach(conflict ->
        {
            assertTrue(conflict.getHasChoice());
            assertTrue(conflict.getChosenLocal());
        });

    }

    @Test
    public void solveDeleteRemoteParentRemote() throws SqlQueriesException {
        {
            Stage bbbtext = creationRemoteDao.get("bbb.txt");
            stageDao.deleteStageById(bbbtext.getId());
            Stage bb = creationRemoteDao.get("bb");
            stageDao.deleteStageById(bb.getId());
            Stage b = creationRemoteDao.get("b");
            b.setDeleted(true);
            stageDao.update(b);
        }
        createConflictSolver().findConflicts();
        assertEquals(3, conflictSolver.getConflictMap().size());

        Conflict bRemote = conflictSolver.getLocalStageConflictMap().values().stream().filter(conflict -> conflict.getRemoteStage().getNamePair().equalsValue("b")).findFirst().get();
        bRemote.decideRemote();

        conflictSolver.getLocalStageConflictMap().values().forEach(conflict ->
        {
            assertTrue(conflict.getHasChoice());
            assertTrue(conflict.getChosenRemote());
        });
        conflictSolver.getRemoteStageConflictMap().values().forEach(conflict ->
        {
            assertTrue(conflict.getHasChoice());
            assertTrue(conflict.getChosenRemote());
        });

    }

    @Test
    public void mergeDeleteRemoteParentRemote() throws Exception {
        solveDeleteRemoteParentRemote();
        conflictSolver.merge();
    }

    @Test
    public void noConflict() throws SqlQueriesException {
        createConflictSolver().findConflicts();
        conflictSolver.getRemoteStageConflictMap().values().forEach(Conflict::decideRemote);
        assertFalse(conflictSolver.hasConflicts());
    }


    @After
    public void after() throws SqlQueriesException {
        creationLocalDao.cleanUp();
        super.after();
    }
}
