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

    public MeinStandAloneAuthFX(MeinAuthService meinAuthService) throws Exception {
        meinAuthService.addMeinAuthAdmin(MeinAuthAdminFX.load(meinAuthService));
    }
}
