package de.mein.dump

import de.mein.auth.data.db.Certificate
import de.mein.auth.data.db.ServiceJoinServiceType
import de.mein.auth.file.AFile
import de.mein.auth.tools.N
import de.mein.drive.gui.DriveFXCreateController

class DumpFxCreateController : DriveFXCreateController() {
    override fun createInstance(name: String?, isServer: Boolean, path: String?, useSymLinks: Boolean): Boolean {
        return N.result({
            val dumpCreateServiceHelper = DumpCreateServiceHelper(meinAuthService)
            if (isServer) dumpCreateServiceHelper.createServerService(name, AFile.instance(path), 0.1f, 30, useSymLinks) else {
                val certificate: Certificate? = this.selectedCertificate
                val serviceJoinServiceType: ServiceJoinServiceType? = this.selectedService
                dumpCreateServiceHelper.createClientService(name, AFile.instance(path), certificate!!.id.v(), serviceJoinServiceType!!.uuid.v(), 0.1f, 30, useSymLinks)
            }
            true
        }, false)!!
    }
}