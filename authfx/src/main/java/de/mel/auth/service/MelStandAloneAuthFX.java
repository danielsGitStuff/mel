package de.mel.auth.service;

/**
 * Created by xor on 6/23/16.
 */
public class MelStandAloneAuthFX {

    public MelStandAloneAuthFX(MelAuthServiceImpl melAuthService) throws Exception {
        melAuthService.addMelAuthAdmin(MelAuthAdminFX.load(melAuthService));
    }
}
