package de.mein.auth.service

import de.mein.auth.jobs.AConnectJob
import de.mein.auth.socket.MeinAuthSocket
import de.mein.auth.socket.MeinValidationProcess

class IncomingConnectionJob(val meinAuthSocket: MeinAuthSocket) : AConnectJob<MeinValidationProcess,Void>() {
}