package de.mel.auth.service;

import de.mel.auth.data.JsonSettings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.ServiceType;
import de.mel.auth.file.AFile;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;

import java.io.File;

/**
 * Created by xor on 6/23/16.
 */
public class MelStandAloneAuthFX {

    public MelStandAloneAuthFX(MelAuthService melAuthService) throws Exception {
        melAuthService.addMelAuthAdmin(MelAuthAdminFX.load(melAuthService));
    }
}
