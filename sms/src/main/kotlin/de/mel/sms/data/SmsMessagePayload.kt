package de.mel.sms.data

import de.mel.auth.data.ServicePayload
import de.mel.core.serialize.SerializableEntity

class SmsPlaintext() : SerializableEntity {
    var address: String = ""
    var dateMs: Long = 0L
    var type: Int = 1 // 1 = inbox, 2 = sent
    var body: String = ""
    var threadId: Long = 0L

    constructor(address: String, dateMs: Long, type: Int, body: String, threadId: Long = 0L) : this() {
        this.address = address
        this.dateMs = dateMs
        this.type = type
        this.body = body
        this.threadId = threadId
    }

    fun canonicalKey(): String = "$address|$dateMs|$type|$body"
}

class EncryptedSmsRecord() : SerializableEntity {
    var serverId: Long? = null
    var msgHash: String = ""
    var iv: String = ""
    var ciphertext: String = ""
    var createdAt: Long = 0L

    constructor(serverId: Long?, msgHash: String, iv: String, ciphertext: String, createdAt: Long) : this() {
        this.serverId = serverId
        this.msgHash = msgHash
        this.iv = iv
        this.ciphertext = ciphertext
        this.createdAt = createdAt
    }
}

class SmsBatchPayload() : ServicePayload() {
    var messages: MutableList<EncryptedSmsRecord> = mutableListOf()
    var sinceId: Long = 0L
    var secret: String? = null

    constructor(intent: String) : this() {
        this.intent = intent
    }
}
