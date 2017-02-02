package de.mein.auth.service;

import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.ServiceType;

/**
 * Created by xor on 6/23/16.
 */
public class MeinStandAloneAuthFX extends MeinAuthService {
    public MeinStandAloneAuthFX(MeinAuthSettings meinAuthSettings, IDBCreatedListener meinAuthAdmin) throws Exception {
        super(meinAuthSettings, meinAuthAdmin);
        addMeinAuthAdmin(MeinAuthFX.load(this));
        //addMeinAuthAdmin(new MeinAuthFX().setMeinAuthService(this));
    }

    public MeinStandAloneAuthFX(MeinAuthSettings meinAuthSettings) throws Exception {
        this(meinAuthSettings, null);
    }

    public static void main(String[] args) throws Exception {
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        MeinAuthSettings meinAuthSettings = null;
        try {
            meinAuthSettings = (MeinAuthSettings) JsonSettings.load(MeinAuthSettings.DEFAULT_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (meinAuthSettings == null)
            meinAuthSettings = (MeinAuthSettings) new MeinAuthSettings().setDeliveryPort(8001).setPort(8000).setName("MeinAuth:)").setWorkingDirectory(MeinBoot.defaultWorkingDir1).setJsonFile(MeinAuthSettings.DEFAULT_FILE);

        MeinStandAloneAuthFX meinStandAloneAuthFX = new MeinStandAloneAuthFX(meinAuthSettings, databaseManager -> {
            ServiceType type = databaseManager.createServiceType("type.name", "type.desc");
            databaseManager.createService(type.getId().v(), "test name");
        });
        MeinBoot meinBoot = new MeinBoot();
        meinBoot.boot(meinStandAloneAuthFX);
    }
}
