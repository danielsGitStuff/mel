package de.mel.android.sms.controller

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.Threadder
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.android.sms.AndroidSmsReader
import de.mel.auth.service.IMelService
import de.mel.auth.service.MelAuthService
import de.mel.sms.crypto.SmsCrypto
import de.mel.sms.data.SmsPlaintext
import de.mel.sms.service.SmsClientService
import de.mel.sms.service.SmsServerService
import de.mel.sms.service.SmsService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidSmsEditController(
    private val melAuthService: MelAuthService,
    activity: MainActivity,
    runningInstance: IMelService,
    embedded: ViewGroup
) : AndroidServiceGuiController(activity, embedded, R.layout.embedded_sms_edit) {

    private val smsService = runningInstance as SmsService
    private lateinit var lblRole: TextView
    private lateinit var btnSync: Button
    private lateinit var txtSecret: EditText
    private lateinit var btnGen: Button
    private lateinit var chkShare: CheckBox
    private lateinit var txtSearch: EditText
    private lateinit var listMessages: ListView

    private var allMessages: List<SmsPlaintext> = emptyList()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        val isClient = smsService is SmsClientService
        lblRole.text = "Role: ${smsService.settings.role}"
        txtSecret.setText(smsService.settings.secret ?: "")
        chkShare.visibility = if (isClient) View.VISIBLE else View.GONE
        chkShare.isChecked = smsService.settings.shareSecretWithServer
        btnSync.visibility = if (isClient) View.VISIBLE else View.GONE
        smsService.addOnChangedListener { activity.runOnUiThread { reloadMessages() } }
        reloadMessages()
    }

    override fun init() {
        lblRole = rootView.findViewById(R.id.lblSmsRole)
        btnSync = rootView.findViewById(R.id.btnSmsSync)
        txtSecret = rootView.findViewById(R.id.txtSmsSecret)
        btnGen = rootView.findViewById(R.id.btnSmsGenSecret)
        chkShare = rootView.findViewById(R.id.chkSmsShareSecret)
        txtSearch = rootView.findViewById(R.id.txtSmsSearch)
        listMessages = rootView.findViewById(R.id.listSmsMessages)

        btnGen.setOnClickListener { txtSecret.setText(SmsCrypto.generateRandomSecret()) }
        btnSync.setOnClickListener {
            val svc = smsService
            if (svc is SmsClientService) {
                Threadder.runNoTryThread {
                    AndroidSmsReader.syncNewSms(activity, svc)
                    svc.pullFromServer()
                }
            }
        }
        txtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun reloadMessages() {
        if (!smsService.hasSecret()) {
            allMessages = emptyList()
            lblRole.text = "Role: ${smsService.settings.role} (Locked)"
            listMessages.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, listOf("Locked: set secret and tap Apply"))
            return
        }
        Threadder.runNoTryThread {
            val t0 = android.os.SystemClock.elapsedRealtime()
            de.mel.Lok.debug("AndroidSmsEditController: loading & decrypting local SMS...")
            val decrypted = smsService.decryptAllLocal()
            de.mel.Lok.debug("AndroidSmsEditController: decrypted ${decrypted.size} SMS in ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            activity.runOnUiThread {
                allMessages = decrypted
                lblRole.text = "Role: ${smsService.settings.role} (${decrypted.size} msgs)"
                renderList()
            }
        }
    }

    private fun renderList() {
        if (!smsService.hasSecret()) return
        val q = txtSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val lines = allMessages
            .filter { q.isEmpty() || it.address.lowercase().contains(q) || it.body.lowercase().contains(q) }
            .map { m ->
                val who = if (m.type == 2) "Me -> ${m.address}" else m.address
                "[${dateFmt.format(Date(m.dateMs))}] $who: ${m.body}"
            }
        listMessages.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, lines)
    }

    override fun onOkClicked(): Boolean {
        val secret = txtSecret.text?.toString()?.trim().orEmpty()
        when (val svc = smsService) {
            is SmsServerService -> svc.setLocalSecret(secret)
            is SmsClientService -> svc.updateSecret(secret, chkShare.isChecked)
        }
        reloadMessages()
        return false
    }
}
