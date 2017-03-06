package de.mein.android;

import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;

/**
 * Created by xor on 3/6/17.
 */
public class AndroidRegHandler implements IRegisterHandler {
    public static final String REGBUNDLE_UUID = "r";
    private static final Map<String, RegBundle> regBundles = new HashMap<>();

    private final MeinAuthService meinAuthService;
    private final Context context;

    public AndroidRegHandler(Context context, MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        this.context = context;
    }

    public static RegBundle retrieveRegBundle(String uuid) {
        RegBundle bundle = regBundles.get(uuid);
        regBundles.remove(uuid);
        return bundle;
    }

    @Override
    public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
        String uuid = UUID.randomUUID().toString();
        RegBundle regBundle = new RegBundle()
                .setListener(listener)
                .setRequest(request)
                .setMyCert(myCertificate)
                .setRemoteCert(certificate);
        regBundles.put(uuid, regBundle);
        Intent i = new Intent();
        i.setClass(context, CertActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(REGBUNDLE_UUID, uuid);
        context.startActivity(i);
    }

}
