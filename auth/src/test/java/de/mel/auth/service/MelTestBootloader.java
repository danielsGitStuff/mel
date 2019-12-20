package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.sql.SqlQueriesException;

import java.io.File;

/**
 * Created by xor on 12/15/16.
 */
public class MelTestBootloader extends Bootloader<MelTestService> {
    public static int count = 0;

    public MelTestBootloader() {
        Lok.debug("debug");
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "test description";
    }

    @Override
    public MelTestService bootLevelShortImpl(MelAuthServiceImpl melAuthService, Service serviceDescription) throws BootException {
        MelTestService testService = new MelTestService(melAuthService, new File("testworkingdir"), 1L, "test uuid no. " + count++);
        try {
            melAuthService.registerMelService(testService);
        } catch (SqlQueriesException e) {
            throw new BootException(this, e);
        }
        return testService;
    }

    @Override
    public void cleanUpDeletedService(MelTestService melService, String uuid) {

    }

    @Override
    public boolean isCompatiblePartner(ServiceJoinServiceType service) {
        return true;
    }


}
