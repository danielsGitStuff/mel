package de.mein.msg

import de.mein.auth.data.db.Service
import de.mein.auth.service.BootLoader
import de.mein.auth.service.MeinAuthService
import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject
import java.lang.Exception

class MsgBootloader : BootLoader() {
    override fun getName(): String = "holyMessenger"

    override fun getDescription(): String = "messages"

    override fun boot(meinAuthService: MeinAuthService?, services: MutableList<Service>?): Promise<Void, Exception, Void> {
        return DeferredObject();
    }
}