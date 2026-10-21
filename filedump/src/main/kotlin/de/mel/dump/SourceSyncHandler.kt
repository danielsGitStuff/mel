package de.mel.dump

import de.mel.auth.service.MelAuthService
import de.mel.filesync.service.MelFileSyncClientService
import de.mel.filesync.service.sync.ClientSyncHandler

/**
 * The source only gives. It never asks the target for its changes: the target holds what other sources gave, and none of it comes back (yet).
 */
class SourceSyncHandler(melAuthService: MelAuthService, service: MelFileSyncClientService) : ClientSyncHandler(melAuthService, service) {
    override fun syncFromServer() {
    }
}
