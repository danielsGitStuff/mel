package de.mel.android.location.controller

import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.Threadder
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.auth.service.IMelService
import de.mel.location.data.LocationStrings
import de.mel.location.data.db.LocationPoint
import de.mel.location.service.LocationService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidLocationEditController(
    activity: MainActivity,
    runningInstance: IMelService,
    embedded: ViewGroup
) : AndroidServiceGuiController(activity, embedded, R.layout.embedded_location_edit) {

    private val locationService = runningInstance as LocationService
    private lateinit var lblEndpoint: TextView
    private lateinit var txtPort: EditText
    private lateinit var txtBind: EditText
    private lateinit var txtUser: EditText
    private lateinit var txtPass: EditText
    private lateinit var txtFilterTid: EditText
    private lateinit var listPoints: ListView

    private var cachedPoints: List<LocationPoint> = emptyList()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        val s = locationService.settings
        lblEndpoint.text = "OwnTracks HTTP: http://${s.bindAddress}:${s.port}/pub"
        txtPort.setText(s.port.toString())
        txtBind.setText(s.bindAddress)
        txtUser.setText(s.username)
        txtPass.setText(s.password)
        locationService.addOnChangedListener { activity.runOnUiThread { reloadPoints() } }
        reloadPoints()
    }

    override fun init() {
        lblEndpoint = rootView.findViewById(R.id.lblLocEndpoint)
        txtPort = rootView.findViewById(R.id.txtLocPort)
        txtBind = rootView.findViewById(R.id.txtLocBind)
        txtUser = rootView.findViewById(R.id.txtLocUser)
        txtPass = rootView.findViewById(R.id.txtLocPass)
        txtFilterTid = rootView.findViewById(R.id.txtLocFilterTid)
        listPoints = rootView.findViewById(R.id.listLocPoints)

        txtFilterTid.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderPoints()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun reloadPoints() {
        Threadder.runNoTryThread {
            val t0 = android.os.SystemClock.elapsedRealtime()
            de.mel.Lok.debug("AndroidLocationEditController: loading recent locations...")
            val pts = locationService.loadRecent(200)
            de.mel.Lok.debug("AndroidLocationEditController: loaded ${pts.size} locations in ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            activity.runOnUiThread {
                cachedPoints = pts
                val s = locationService.settings
                lblEndpoint.text = "OwnTracks HTTP: http://${s.bindAddress}:${s.port}/pub (${pts.size} recent)"
                renderPoints()
            }
        }
    }

    private fun renderPoints() {
        val filter = txtFilterTid.text?.toString()?.trim()?.lowercase().orEmpty()
        val lines = cachedPoints
            .filter { filter.isEmpty() || it.tid.v().lowercase().contains(filter) }
            .map { pt ->
                val ts = dateFmt.format(Date(pt.tst.v() * 1000L))
                "$ts [${pt.tid.v()}] ${pt.lat.v()}, ${pt.lon.v()}"
            }
        listPoints.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, lines)
    }

    override fun onOkClicked(): Boolean {
        val port = txtPort.text?.toString()?.trim()?.toIntOrNull() ?: LocationStrings.DEFAULT_PORT
        val bind = txtBind.text?.toString()?.trim().orEmpty().ifEmpty { "0.0.0.0" }
        val user = txtUser.text?.toString()?.trim().orEmpty()
        val pass = txtPass.text?.toString() ?: ""
        Threadder.runNoTryThread {
            locationService.updateSettings(port, bind, user, pass)
        }
        lblEndpoint.text = "OwnTracks HTTP: http://$bind:$port/pub"
        return false
    }
}
