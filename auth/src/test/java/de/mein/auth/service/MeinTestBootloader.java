package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.data.db.Service;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by xor on 12/15/16.
 */
public class MeinTestBootloader extends Bootloader {
    public static int count = 0;

    public MeinTestBootloader(){
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
    public Promise<Void, Exception, Void> boot(MeinAuthService meinAuthService , List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        MeinTestService testService = new MeinTestService(meinAuthService, new File("testworkingdir"),1L,"test uuid no. " + count++);
        meinAuthService.registerMeinService(testService);
        return null;
    }
}
