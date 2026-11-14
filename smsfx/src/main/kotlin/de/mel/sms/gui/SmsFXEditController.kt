package de.mel.sms.gui

import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.gui.ServiceSettingsFX
import de.mel.auth.service.MelAuthAdminFX
import de.mel.sms.crypto.SmsCrypto
import de.mel.sms.data.SmsPlaintext
import de.mel.sms.service.SmsClientService
import de.mel.sms.service.SmsServerService
import de.mel.sms.service.SmsService
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import java.text.SimpleDateFormat
import java.util.Date

class SmsFXEditController : ServiceSettingsFX<SmsService>() {

    @FXML
    private lateinit var lblRole: Label

    @FXML
    private lateinit var lblStatus: Label

    @FXML
    private lateinit var txtSecret: TextField

    @FXML
    private lateinit var chkShareSecret: CheckBox

    @FXML
    private lateinit var btnSync: Button

    @FXML
    private lateinit var txtSearch: TextField

    @FXML
    private lateinit var listThreads: ListView<String>

    @FXML
    private lateinit var listMessages: ListView<String>

    private var allMessages: List<SmsPlaintext> = emptyList()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
    private val changeListener: () -> Unit = { Platform.runLater { reloadMessages() } }

    override fun init() {
        txtSearch.textProperty().addListener { _, _, _ -> refreshThreadsAndMessages() }
        listThreads.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            renderThread(selected)
        }
    }

    override fun feed(serviceJoinServiceType: ServiceJoinServiceType) {
        super.feed(serviceJoinServiceType)
        val svc = service
        lblRole.text = svc.settings.role
        txtSecret.text = svc.settings.secret ?: ""
        val isClient = svc is SmsClientService
        chkShareSecret.isVisible = isClient
        chkShareSecret.isManaged = isClient
        chkShareSecret.isSelected = svc.settings.shareSecretWithServer
        btnSync.isVisible = isClient
        btnSync.isManaged = isClient
        svc.addOnChangedListener(changeListener)
        reloadMessages()
    }

    @FXML
    fun onGenerateSecret() {
        txtSecret.text = SmsCrypto.generateRandomSecret()
    }

    @FXML
    fun onApplySecret() {
        val svc = service ?: return
        val secret = txtSecret.text?.trim().orEmpty()
        when (svc) {
            is SmsServerService -> svc.setLocalSecret(secret)
            is SmsClientService -> svc.updateSecret(secret, chkShareSecret.isSelected)
        }
        reloadMessages()
    }

    @FXML
    fun onSyncNow() {
        val svc = service
        if (svc is SmsClientService) {
            svc.pullFromServer()
        }
    }

    private fun reloadMessages() {
        val svc = service ?: return
        if (!svc.hasSecret()) {
            allMessages = emptyList()
            lblStatus.text = getString("edit.locked")
            listThreads.items.clear()
            listMessages.items.clear()
            return
        }
        allMessages = svc.decryptAllLocal()
        lblStatus.text = "${allMessages.size} messages"
        refreshThreadsAndMessages()
    }

    private fun refreshThreadsAndMessages() {
        val q = txtSearch.text?.trim()?.lowercase().orEmpty()
        val filtered = if (q.isEmpty()) allMessages else allMessages.filter {
            it.address.lowercase().contains(q) || it.body.lowercase().contains(q)
        }
        val addresses = filtered.map { it.address }.distinct()
        val prev = listThreads.selectionModel.selectedItem
        listThreads.items.setAll(addresses)
        if (prev != null && addresses.contains(prev)) {
            listThreads.selectionModel.select(prev)
        } else if (addresses.isNotEmpty()) {
            listThreads.selectionModel.select(0)
        } else {
            listMessages.items.clear()
        }
    }

    private fun renderThread(address: String?) {
        if (address == null) {
            listMessages.items.clear()
            return
        }
        val q = txtSearch.text?.trim()?.lowercase().orEmpty()
        val msgs = allMessages
            .filter { it.address == address && (q.isEmpty() || it.body.lowercase().contains(q) || it.address.lowercase().contains(q)) }
            .sortedBy { it.dateMs }
            .map { m ->
                val dir = if (m.type == 2) "Me" else m.address
                "[${dateFmt.format(Date(m.dateMs))}] $dir: ${m.body}"
            }
        listMessages.items.setAll(msgs)
    }

    override fun onPrimaryClicked(melAuthAdminFX: MelAuthAdminFX): Boolean {
        onApplySecret()
        return true
    }

    override fun getTitle(): String = getString("edit.title")

    override fun configureParentGui(melAuthAdminFX: MelAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("edit.apply"))
        melAuthAdminFX.setSecondaryButtonText(getString("edit.delete"))
    }
}
