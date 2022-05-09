package de.mel.dump

import de.mel.auth.data.MelAuthSettings
import de.mel.auth.data.MelRequest
import de.mel.auth.data.db.Certificate
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.DefaultFileConfiguration
import de.mel.auth.file.IFile
import de.mel.auth.service.MelAuthServiceImpl
import de.mel.auth.service.MelBoot
import de.mel.auth.service.power.PowerManager
import de.mel.auth.socket.process.reg.IRegisterHandler
import de.mel.auth.socket.process.reg.IRegisterHandlerListener
import de.mel.auth.tools.N
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory
import de.mel.filesync.bash.BashTools
import de.mel.sql.RWLock
import de.mel.sql.deserialize.PairDeserializerFactory
import de.mel.sql.serialize.PairSerializerFactory
import java.io.File

class MainTest {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance())
            FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance())
            FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance())
            FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance())
            AbstractFile.configure(DefaultFileConfiguration() as AbstractFile.Configuration<IFile>)
            BashTools.Companion.init()

            val settings = MelAuthSettings.createDefaultSettings()
            val rootFile = File("dump.test.dir")
            rootFile.mkdir()
            val root = AbstractFile.instance(rootFile)
            val boot = MelBoot(settings, PowerManager(settings), DumpBootloader::class.java)
            boot.boot().done { mas ->
                mas.addRegisterHandler(object : IRegisterHandler {
                    override fun onRegistrationCompleted(partnerCertificate: Certificate?) {

                    }

                    override fun onLocallyAccepted(partnerCertificate: Certificate?) {

                    }

                    override fun onRemoteAccepted(partnerCertificate: Certificate?) {

                    }

                    override fun onRemoteRejected(partnerCertificate: Certificate?) {

                    }

                    override fun acceptCertificate(listener: IRegisterHandlerListener?, request: MelRequest?, myCertificate: Certificate?, certificate: Certificate?) {
                        listener?.onCertificateAccepted(request, certificate)
                    }

                    override fun onLocallyRejected(partnerCertificate: Certificate?) {

                    }
                })
                mas.addRegisteredHandler { melAuthService, cert ->
                    melAuthService.databaseManager.allServices.forEach {
                        N.oneLine {
                            mas.databaseManager.grant(it.serviceId.v(), cert.id.v())
                        }
                    }
                }
                val helper = DumpCreateServiceHelper(mas as MelAuthServiceImpl)
                helper.createServerService("TEST Dump Server", root, .5f, 30, false)
            }
            val lock = RWLock()
            lock.lockWrite().lockWrite()
        }
    }
}