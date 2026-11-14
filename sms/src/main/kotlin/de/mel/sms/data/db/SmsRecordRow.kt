package de.mel.sms.data.db

import de.mel.sql.Pair
import de.mel.sql.SQLTableObject

class SmsRecordRow : SQLTableObject() {
    val id = Pair(Long::class.java, "id")
    val msgHash = Pair(String::class.java, "msg_hash")
    val iv = Pair(String::class.java, "iv")
    val ciphertext = Pair(String::class.java, "ciphertext")
    val createdAt = Pair(Long::class.java, "created_at")

    init {
        init()
    }

    override fun getTableName(): String = "sms_messages"

    override fun init() {
        populateInsert(msgHash, iv, ciphertext, createdAt)
        populateAll(id)
    }
}
