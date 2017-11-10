package de.mein.drive.quota;

import de.mein.auth.tools.N;
import de.mein.drive.data.DriveSettings;
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
    private DriveSettings driveSettings;

    public void getRequiredSpace(ISQLQueries sqlQueries, Long stageSetId) throws SqlQueriesException, OutOfSpaceException {
        Stage nStage = new Stage();
        TransferDetails nTransfer = new TransferDetails();
        FsFile nFsEntry = new FsFile();
        Waste nWaste = new Waste();
        /**
         * sum up everything we have to transfer
         * - (literally MINUS) all would be canceled transfers
         * - all progress already made by required transfers
         * - stuff that is required and in WasteBin
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
                        "   s." + nStage.getStageSetPair().k() + "=?" +
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
        final Long[] availableSpace = {driveSettings.getRootDirectory().getOriginalFile().getFreeSpace()};
        //try to clean up wastebin if that happens
        if (requiredSpace > availableSpace[0]) {
            WasteDummy wasteDummy = new WasteDummy();
            query = "select w." + nWaste.getId().k() + ", w." + nWaste.getSize().k() + " from " + nWaste.getTableName() + " w left join (select ss." + nStage.getStageSetPair().k() + ", ss." + nStage.getIdPair().k() + ", ss." + nStage.getContentHashPair().k() + " from " + nStage.getTableName() + " ss " +
                    "where ss." + nStage.getStageSetPair().k() + "=? ) s on w." + nWaste.getHash().k() + "=s." + nStage.getContentHashPair().k() + " where s." + nStage.getIdPair().k() + " is null " +
                    "order by w." + nWaste.getDeleted().k();
            N.readSqlResource(sqlQueries.loadQueryResource(query,wasteDummy.getAllAttributes(),WasteDummy.class,ISQLQueries.whereArgs(stageSetId)),(sqlResource, wasteDum) -> {
                availableSpace[0] += wasteDum.getSize().v();
                if (requiredSpace < availableSpace[0])
                    sqlResource.close();
            });
            if (requiredSpace > availableSpace[0]){
                throw new OutOfSpaceException();
            }
        }
    }
}
