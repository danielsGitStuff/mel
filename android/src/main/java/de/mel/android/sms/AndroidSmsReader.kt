package de.mel.android.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import de.mel.sms.data.SmsPlaintext
import de.mel.sms.service.SmsClientService

object AndroidSmsReader {

    fun hasReadSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun readSince(context: Context, sinceDateMs: Long): List<SmsPlaintext> {
        if (!hasReadSmsPermission(context)) return emptyList()
        val result = mutableListOf<SmsPlaintext>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.BODY,
            Telephony.Sms.THREAD_ID
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(sinceDateMs.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return emptyList()

        cursor.use { c ->
            val idxAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val idxDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val idxType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val idxBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val idxThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            while (c.moveToNext()) {
                result.add(
                    SmsPlaintext(
                        address = c.getString(idxAddr) ?: "",
                        dateMs = c.getLong(idxDate),
                        type = c.getInt(idxType),
                        body = c.getString(idxBody) ?: "",
                        threadId = c.getLong(idxThread)
                    )
                )
            }
        }
        return result
    }

    fun syncNewSms(context: Context, client: SmsClientService) {
        if (!client.hasSecret()) return
        val newMsgs = readSince(context, client.settings.lastSyncedDateMs)
        if (newMsgs.isNotEmpty()) {
            client.pushPlaintextMessages(newMsgs)
        }
    }
}
