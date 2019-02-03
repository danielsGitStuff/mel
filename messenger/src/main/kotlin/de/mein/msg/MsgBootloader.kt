package de.mein.msg

import de.mein.auth.data.db.Service
import de.mein.auth.service.Bootloader
import de.mein.auth.service.MeinAuthService
import org.jdeferred.Deferred
import org.jdeferred.Promise
import org.jdeferred.impl.DefaultDeferredManager
import org.jdeferred.impl.DeferredObject
import java.io.File
import java.lang.Exception

class MsgBootloader : Bootloader() {
    override fun getName(): String = "holyMessenger"

    override fun getDescription(): String = "messages"

    override fun bootStage1Impl(meinAuthService: MeinAuthService, service: Service): Promise<Void, BootException, Void> {
        val booted = DeferredObject<Void, BootException, Void>()
        val deferredManager = DefaultDeferredManager()
        val promises = mutableListOf<Deferred<Void, Exception, Void>>()
        val workingDir = File(bootLoaderDir, service.uuid.v())
        val messenger = MessengerService(meinAuthService, workingDir, service.typeId.v(), service.uuid.v())
        promises + messenger.startedDeferred
        deferredManager.`when`(*promises.toTypedArray()).done { booted.resolve(null) }.fail { result -> booted.reject(BootException(this, result.reject as Exception?)) }
        return booted
    }
}