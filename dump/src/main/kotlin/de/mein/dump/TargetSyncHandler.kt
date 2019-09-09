package de.mein.dump

import de.mein.Lok
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.process.`val`.Request
import de.mein.auth.tools.lock.T
import de.mein.auth.tools.lock.Transaction
import de.mein.drive.data.Commit
import de.mein.drive.service.sync.ServerSyncHandler

class TargetSyncHandler(meinAuthService: MeinAuthService, targetService: TargetService) : ServerSyncHandler(meinAuthService, targetService) {
    override fun handleCommit(request: Request<*>?) {
        val commit = request!!.payload as Commit
        val transaction: Transaction<*> = T.lockingTransaction(fsDao)
        try {
            executeCommit(request, commit, transaction)
        } finally {
            transaction.end()
        }
        transferManager.research()
        Lok.debug("MeinDriveServerService.handleCommit")
    }
}