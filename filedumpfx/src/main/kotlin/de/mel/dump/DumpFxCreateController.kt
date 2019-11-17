package de.mel.dump

import de.mel.auth.data.db.Certificate
import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.file.AbstractFile
import de.mel.auth.tools.N
import de.mel.filesync.gui.FileSyncFXCreateController

class DumpFxCreateController : FileSyncFXCreateController() {
    override fun createInstance(name: String, isServer: Boolean, path: String, useSymLinks: Boolean): Boolean {
        return N.result({
            val dumpCreateServiceHelper = DumpCreateServiceHelper(melAuthService)
            if (isServer) dumpCreateServiceHelper.createServerService(name, AbstractFile.instance(path), 0.1f, 30, useSymLinks) else {
                val certificate: Certificate? = this.selectedCertificate
                val serviceJoinServiceType: ServiceJoinServiceType? = this.selectedService
                dumpCreateServiceHelper.createClientService(name, AbstractFile.instance(path), certificate!!.id.v(), serviceJoinServiceType!!.uuid.v(), 0.1f, 30, useSymLinks)
            }
            true
        }, false)!!
    }
}