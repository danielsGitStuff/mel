package de.mein.auth.service;

import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;

import java.io.File;

/**
 * Created by xor on 6/23/16.
 */
public class MeinStandAloneAuthFX {
    private MeinAuthService meinAuthService;

    public MeinStandAloneAuthFX(MeinAuthService meinAuthService) throws Exception {
        this.meinAuthService = meinAuthService;
        meinAuthService.addMeinAuthAdmin(MeinAuthAdminFX.load(meinAuthService));
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
        meinAuthSettings.setIdbCreatedListener(databaseManager -> {
            ServiceType type = databaseManager.createServiceType("type.name", "type.desc");
            databaseManager.createService(type.getId().v(), "test name");
        });
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings));
        meinBoot.boot().done(mas -> N.r(() -> new MeinStandAloneAuthFX(mas))).fail(result -> System.err.println("dfh9430f"));
    }
}
