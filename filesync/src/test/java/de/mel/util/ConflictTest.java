package de.mel.util;

import de.mel.Lok;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.sql.SqlQueriesException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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

/**
 * default dir structure:
 * [root]
 * |_a
 * | |_aa.txt
 * |_b
 * | |_bb
 * |    |_bbb.txt
 * |_c
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


    /**
     * [root]
     * |_a
     * | |_aa.txt
     * | |_i0         <---
     * |    |_J.txt   <---
     * |_b
     * | |_bb
     * |    |_bbb.txt
     * |_c
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findContentNewDirConflict() throws SqlQueriesException {
        Stage a = creationRemoteDao.get("a");
        Stage i0 = new Stage()
                .setName("i0")
                .setIsDirectory(true)
                .setContentHash("i0")
                .setDepth(2)
                .setPath("/a/")
                .setOrder(19L)
                .setStageSet(a.getStageSet())
                .setParentId(a.getId())
                .setModified(12L)
                .setCreated(12L)
                .setFsParentId(a.getFsId())
                .setDeleted(false);
        creationRemoteDao.insert(i0);
        Stage J = new Stage()
                .setName("J.txt")
                .setIsDirectory(false)
                .setContentHash("J.txt")
                .setDepth(2)
                .setPath("/a/i0/")
                .setOrder(20L)
                .setStageSet(i0.getStageSet())
                .setParentId(i0.getId())
                .setModified(12L)
                .setCreated(12L)
                .setFsParentId(i0.getFsId())
                .setDeleted(false);
        a.setContentHash("i0+i0/J added");
        creationRemoteDao.insert(J);
        stageDao.update(a);

        createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getConflictMap().size());
        assertEquals(1, conflictSolver.getLocalStageConflictMap().size());
        assertEquals(3, conflictSolver.getRemoteStageConflictMap().size());
        Map<String, Conflict> conflictMap = conflictSolver.getConflictMap();

        assertTrue(conflictMap.containsKey("2/9"));
        Conflict conflictA = conflictMap.get("2/9");
        assertNotNull(conflictA.getLocalStage());
        assertNotNull(conflictA.getRemoteStage());
        assertEquals(1, conflictA.getChildren().size());

        Conflict conflictI0 = conflictA.getChildren().get(0);
        assertNull(conflictI0.getLocalStage());
        assertNotNull(conflictI0.getRemoteStage());
        assertEquals(1, conflictI0.getChildren().size());

        Conflict conflictJ = conflictI0.getChildren().get(0);
        assertNull(conflictJ.getLocalStage());
        assertNotNull(conflictJ.getRemoteStage());
        assertEquals(0, conflictJ.getChildren().size());
        Lok.debug("success");
//        Conflict conflictI0 = conflictA.addChild()
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
        makeRemote(creationRemoteDao, remoteStageSet.getId().v());
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

    private void makeRemote(StageTestCreationDao dao, Long stageSetId) throws SqlQueriesException {
        Order order = new Order();
        Map<Long, Long> idMap = new HashMap<>();
        N.forEach(stageDao.getStagesByStageSet(stageSetId).toList(), stage -> {
            Stage daoStage = dao.get(stage.getName());
            daoStage.setFsId(order.ord());
            idMap.put(daoStage.getId(), daoStage.getFsId());
            if (daoStage.getParentIdPair().notNull()) {
                daoStage.setFsParentId(idMap.get(daoStage.getParentId()));
            }
            stageDao.update(daoStage);
        });
        StageSet stageSet = stageDao.getStageSetById(stageSetId);
        stageSet.setOriginCertId(1L);
        stageSet.setSource(FileSyncStrings.STAGESET_SOURCE_SERVER);
        stageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
    }

    @Test
    public void mergeDeleteRemoteParentRemote() throws Exception {
        solveDeleteRemoteParentRemote();
        conflictSolver.merge();
        creationMergedDao.reloadStageSet(conflictSolver.mergedStageSet.getId().v());
        assertEquals(5, creationMergedDao.getEntries().size());
        // content of /b/ must not be in this stage set
        assertNull(creationMergedDao.get("bb"));
    }

    @Test
    public void mergeDeleteRemoteParentLocal() throws Exception {
        solveDeleteRemoteParentRemote();
        conflictSolver.getConflictMap().values().forEach(Conflict::decideLocal);
        conflictSolver.merge();
        creationMergedDao.reloadStageSet(conflictSolver.mergedStageSet.getId().v());
        assertEquals(7, creationMergedDao.getEntries().size());
        // content of /b/ must not be in this stage set
        assertNotNull(creationMergedDao.get("bb"));
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
