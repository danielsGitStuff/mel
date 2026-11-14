package de.mel.android.location.controller

import android.view.ViewGroup
import android.widget.EditText
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.location.data.LocationSettings
import de.mel.location.data.LocationStrings

class AndroidLocationCreateController(
    activity: MainActivity,
    embedded: ViewGroup
) : AndroidServiceGuiController(activity, embedded, R.layout.embedded_location_create) {

    private lateinit var txtName: EditText
    private lateinit var txtPort: EditText
    private lateinit var txtBind: EditText
    private lateinit var txtUser: EditText
    private lateinit var txtPass: EditText

    override fun init() {
        txtName = rootView.findViewById(R.id.txtLocName)
        txtPort = rootView.findViewById(R.id.txtLocPort)
        txtBind = rootView.findViewById(R.id.txtLocBind)
        txtUser = rootView.findViewById(R.id.txtLocUser)
        txtPass = rootView.findViewById(R.id.txtLocPass)
    }

    val serviceName: String
        get() = txtName.text?.toString()?.trim().orEmpty().ifEmpty { LocationStrings.NAME }

    fun buildSettings(): LocationSettings = LocationSettings().apply {
        port = txtPort.text?.toString()?.trim()?.toIntOrNull() ?: LocationStrings.DEFAULT_PORT
        bindAddress = txtBind.text?.toString()?.trim().orEmpty().ifEmpty { "0.0.0.0" }
        username = txtUser.text?.toString()?.trim().orEmpty()
        password = txtPass.text?.toString() ?: ""
    }

    override fun onOkClicked(): Boolean = true
}
