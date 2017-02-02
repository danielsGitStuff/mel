package de.mein.auth.gui;

import de.mein.auth.data.db.ServiceJoinServiceType;

/**
 * Created by xor on 10/26/16.
 */
public abstract class ServiceSettingsFX extends AuthSettingsFX {

    public abstract void feed(ServiceJoinServiceType service);
}
