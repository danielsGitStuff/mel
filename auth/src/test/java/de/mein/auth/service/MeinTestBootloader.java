package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.data.db.Service;
import de.mein.sql.SqlQueriesException;

import java.io.File;

/**
 * Created by xor on 12/15/16.
 */
public class MeinTestBootloader extends Bootloader<MeinTestService> {
    public static int count = 0;

    public MeinTestBootloader() {
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
    public MeinTestService bootLevel1Impl(MeinAuthService meinAuthService, Service serviceDescription) throws BootException {
        MeinTestService testService = new MeinTestService(meinAuthService, new File("testworkingdir"), 1L, "test uuid no. " + count++);
        try {
            meinAuthService.registerMeinService(testService);
        } catch (SqlQueriesException e) {
            throw new BootException(this, e);
        }
        return testService;
    }

    @Override
    public void cleanUpDeletedService(MeinTestService meinService, String uuid) {

    }


}
