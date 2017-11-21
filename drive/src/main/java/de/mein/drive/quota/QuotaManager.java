package de.mein.drive.quota;

import de.mein.auth.tools.N;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.Wastebin;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.Waste;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 10.11.2017.
 */

public class QuotaManager {
    private final ISQLQueries sqlQueries;
    private final MeinDriveService meinDriveService;
    private final DriveSettings driveSettings;
    private final Wastebin wastebin;

    public QuotaManager(MeinDriveService meinDriveService) {
        this.meinDriveService = meinDriveService;
        this.driveSettings = meinDriveService.getDriveSettings();
        this.wastebin = meinDriveService.getWastebin();
        this.sqlQueries = meinDriveService.getDriveDatabaseManager().getFsDao().getSqlQueries();
    }

    public void freeSpaceForStageSet(Long stageSetId) throws SqlQueriesException, OutOfSpaceException {
        Stage nStage = new Stage();
        TransferDetails nTransfer = new TransferDetails();
        FsFile nFsEntry = new FsFile();
        Waste nWaste = new Waste();
        /**
         * sum up everything we have to transfer
         * - (literally MINUS) all would be canceled transfers
         * - all progress already made by required transfers
         * - stuff that is required and in Wastebin
         */
        String query =
                "select sum(n*" + nStage.getSizePair().k() + ") as bytestodownload, (sum(n*" + nStage.getSizePair().k() + ")-\n" +
                        "(\n" +
                        "	select sum(t." + nTransfer.getStarted().k() + " * (t." + nTransfer.getTransferred().k() + ")) as deletablebytes from " + nTransfer.getTableName() + " t left join --transfers that cancel\n" +
                        "	(\n" +
                        "		select * from (\n" +
                        "			select " + nStage.getContentHashPair().k() + ", sum(1)  + (select (sum(not " + nStage.getDeletedPair().k() + ") - sum(" + nStage.getDeletedPair().k() + ")) as n from " + nStage.getTableName() + " s where s." + nStage.getContentHashPair().k() + "=ff." + nFsEntry.getContentHash().k() + ") as exis from " + nFsEntry.getTableName() + " ff where " + nFsEntry.getSynced().k() + "=? group by " + nStage.getContentHashPair().k() + "\n" +
                        "		)\n" +
                        "		where exis=?\n" +
                        "	) ex on ex." + nStage.getContentHashPair().k() + " = t." + nTransfer.getHash().k() + " where ex." + nStage.getContentHashPair().k() + " not null group by ex." + nStage.getContentHashPair().k() + "\n" +
                        ")-\n" +
                        "(\n" +
                        "	select sum(t." + nTransfer.getStarted().k() + " * (t." + nTransfer.getTransferred().k() + ")) as deletablebytes from " + nTransfer.getTableName() + " t left join --transfers which would remain and already made progress\n" +
                        "	(\n" +
                        "		select * from (\n" +
                        "			select " + nStage.getContentHashPair().k() + ", sum(1)  + (select (sum(not " + nStage.getDeletedPair().k() + ") - sum(" + nStage.getDeletedPair().k() + ")) as n from " + nStage.getTableName() + " s where s." + nStage.getContentHashPair().k() + "=ff." + nFsEntry.getContentHash().k() + ") as exis from " + nFsEntry.getTableName() + " ff where " + nFsEntry.getSynced().k() + "=? group by " + nStage.getContentHashPair().k() + "\n" +
                        "		)\n" +
                        "		where exis=?\n" +
                        "	) ex on ex." + nStage.getContentHashPair().k() + " = t." + nTransfer.getHash().k() + " where ex." + nStage.getContentHashPair().k() + " is null group by ex." + nStage.getContentHashPair().k() + "\n" +
                        ")\n" +
                        ") as requiredspace from (\n" +
                        "	select (sum(not " + nStage.getDeletedPair().k() + ") - sum(" + nStage.getDeletedPair().k() + ") + (select count(*) from " + nFsEntry.getTableName() + " f where f." + nStage.getContentHashPair().k() + "=s." + nStage.getContentHashPair().k() + " and f." + nFsEntry.getSynced().k() + "=?) - (select count(*) from " + nWaste.getTableName() + " w where w." + nTransfer.getHash().k() + " = s." + nStage.getContentHashPair().k() + " and " + nWaste.getInplace().k() + "=?)) as n, " + nStage.getContentHashPair().k() + ", " + nStage.getSizePair().k() + " from " + nStage.getTableName() + " s " +
                        "   where s." + nStage.getStageSetPair().k() + "=?" +
                        "   group by " + nStage.getContentHashPair().k() + "\n" +
                        ")";
        Long requiredSpace = sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(
                0,//synced
                0,//exis
                0,//synced
                0,//exis
                0,//synced
                1,//inplace
                stageSetId));
        final Long availableSpace = driveSettings.getRootDirectory().getOriginalFile().getFreeSpace();
        //try to clean up wastebin if that happens
        if (requiredSpace > availableSpace) {
            freeSpace(requiredSpace - availableSpace, stageSetId);
        }
    }

    /**
     * @param requiredSpace amount of bytes which shall be free
     * @param stageSetId    Files which are part of this StageSet won't be deleted
     */
    private void freeSpace(long requiredSpace, long stageSetId) throws SqlQueriesException, OutOfSpaceException {
        Stage nStage = new Stage();
        WasteDummy wasteDummy = new WasteDummy();
        Waste nWaste = new Waste();
        Long[] freed = new Long[]{0L};
        String query = "select w." + nWaste.getId().k() + ", w." + nWaste.getSize().k() + " from " + nWaste.getTableName() + " w left join (select ss." + nStage.getStageSetPair().k() + ", ss." + nStage.getIdPair().k() + ", ss." + nStage.getContentHashPair().k() + " from " + nStage.getTableName() + " ss " +
                "where ss." + nStage.getStageSetPair().k() + "=? ) s on w." + nWaste.getHash().k() + "=s." + nStage.getContentHashPair().k() + " where s." + nStage.getIdPair().k() + " is null " +
                "order by w." + nWaste.getDeleted().k();
        N.readSqlResource(sqlQueries.loadQueryResource(query, wasteDummy.getAllAttributes(), WasteDummy.class, ISQLQueries.whereArgs(stageSetId)), (sqlResource, wasteDum) -> {
            //delete until the required space is freed
            freed[0] += wasteDum.getSize().v();
            wastebin.rm(wasteDum.getId().v());
            if (freed[0] > requiredSpace)
                sqlResource.close();
        });
        wastebin.deleteFlagged();
        if (requiredSpace > freed[0]) {
            throw new OutOfSpaceException();
        }
    }

    private void estimateOccupiedSpace() {
        Waste nWaste = new Waste();
        FsFile nFsFile = new FsFile();
        String query = "select \n" +
                "	sum(\n" +
                "		case when sedeleted then \n" +
                "			-ssize --substract stuff that is deleted \n" +
                "		else (\n" +
                "			case when ssize is null then --stage might have another size than fs \n" +
                "				fsize \n" +
                "			else \n" +
                "				ssize \n" +
                "			end \n" +
                "		)\n" +
                "		end \n" +
                "	)\n" +
                "	+\n" +
                "	(--sum up everthing in the wastebin (might be null if nothing in waste table)\n" +
                "	case when(select count(*) from waste w where w.inplace=1)=0 then  \n" +
                "		0 \n" +
                "	else \n" +
                "		(select sum(w.size) from waste w where w.inplace=1)\n" +
                "	end \n" +
                "	)\n" +
                "	+\n" +
                "	(\n" +
                "		select sum(transferred) from transfer\n" +
                "	)\n" +
                "	as summ \n" +
                "from \n" +
                "(\n" +
                "	select f.size as fsize, f.id, sedeleted, ssize from fsentry f left join \n" +
                "	(--join fsentries with the latest stage entries which source from \"fs\"\n" +
                "		select s.size as ssize, s.fsid, deleted as sedeleted from stage s, stageset ss on s.stageset=ss.id where ss.source=\"fs\" order by ss.created \n" +
                "	) \n" +
                "	s on f.id=s.fsid \n" +
                "where f.dir=0 group by f.id);";
    }
}
