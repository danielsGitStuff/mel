package de.mein.msg

import de.mein.auth.data.db.Service
import de.mein.auth.service.BootLoader
import de.mein.auth.service.MeinAuthService
import org.jdeferred.Deferred
import org.jdeferred.DeferredManager
import org.jdeferred.Promise
import org.jdeferred.impl.DefaultDeferredManager
import org.jdeferred.impl.DeferredObject
import java.io.File
import java.lang.Exception

class MsgBootloader : BootLoader() {
    override fun getName(): String = "holyMessenger"

    override fun getDescription(): String = "messages"

    override fun boot(meinAuthService: MeinAuthService, services: MutableList<Service>): Promise<Void, Exception, Void> {
        val booted = DeferredObject<Void, Exception, Void>()
        val deferredManager = DefaultDeferredManager()
        val promises = mutableListOf<Deferred<Void, Exception, Void>>()
        for (service in services) {
            val workingDir = File(bootLoaderDir, service.uuid.v())
            val messenger = MessengerService(meinAuthService, workingDir, service.typeId.v(), service.uuid.v())
            promises + messenger.startedDeferred
        }
        deferredManager.`when`(*promises.toTypedArray()).done { booted.resolve(null) }.fail { result -> booted.reject(result.reject as Exception?) }
        return booted
    }
}