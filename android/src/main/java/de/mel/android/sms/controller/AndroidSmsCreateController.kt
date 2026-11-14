package de.mel.android.sms.controller

import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.controller.RemoteServiceChooserController
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.sms.crypto.SmsCrypto

class AndroidSmsCreateController(
    melAuthService: MelAuthService,
    activity: MainActivity,
    viewGroup: ViewGroup,
    bootloader: Bootloader<*>
) : RemoteServiceChooserController(melAuthService, activity, viewGroup, R.layout.embedded_sms_create, bootloader) {

    private lateinit var txtSecret: EditText
    private lateinit var btnGen: Button
    private lateinit var chkShare: CheckBox

    override fun initEmbedded() {
        txtSecret = rootView.findViewById(R.id.txtSmsSecret)
        btnGen = rootView.findViewById(R.id.btnSmsGenSecret)
        chkShare = rootView.findViewById(R.id.chkSmsShareSecret)
        btnGen.setOnClickListener { txtSecret.setText(SmsCrypto.generateRandomSecret()) }
    }

    val secret: String?
        get() = txtSecret.text?.toString()?.trim()

    val shareSecretWithServer: Boolean
        get() = chkShare.isChecked

    override fun onOkClicked(): Boolean = true

    override fun getPermissionsText(): Int = R.string.permissionSms
}
