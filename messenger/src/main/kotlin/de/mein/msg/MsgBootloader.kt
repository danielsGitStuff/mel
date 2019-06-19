package de.mein.msg

import de.mein.auth.service.BootException
import de.mein.auth.service.Bootloader
import de.mein.auth.service.MeinAuthService
import org.jdeferred.Deferred
import org.jdeferred.impl.DefaultDeferredManager
import org.jdeferred.impl.DeferredObject
import java.io.File


class MsgBootloader : Bootloader<MessengerService>() {
    override fun getName(): String = "holyMessenger"

    override fun getDescription(): String = "messages"

    override fun bootLevel1Impl(meinAuthService: MeinAuthService, service: Service): MessengerService {
        val booted = DeferredObject<MessengerService, BootException, Void>()
        val deferredManager = DefaultDeferredManager()
        val promises = mutableListOf<Deferred<Void, Exception, Void>>()
        val workingDir = File(bootLoaderDir, service.uuid.v())
        val messenger = MessengerService(meinAuthService, workingDir, service.typeId.v(), service.uuid.v())
        promises + messenger.startedDeferred
        deferredManager.`when`(*promises.toTypedArray()).done { booted.resolve(null) }.fail { result -> booted.reject(BootException(this, result.reject as Exception)) }
        return messenger
    }
}