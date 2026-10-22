package de.mel.android.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.mel.AndroidPermission
import de.mel.R
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer

class PermissionsActivity : AppCompatActivity() {

    companion object {
        const val PERMISSIONS_PAYLOAD = "permissionsPayload"
    }

    private var allPermissions: List<AndroidPermission> = emptyList()

    private val genericPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
            permissionsMap.forEach { (permission, isGranted) ->
                if (isGranted) permissionsGranted.add(permission) else permissionsGranted.remove(permission)
            }
            updateList()
        }

    private val managerStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                permissionsGranted.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    permissionsGranted.remove(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                }
            }
            updateList()
        }

    lateinit var ls: RecyclerView
    private val permissionsGranted = mutableSetOf<String>()

    private fun updateList() {
        val pm = PermissionsManager2(this, emptyList())
        val permissionsNotGranted =
            allPermissions.filter { !pm.hasPermission(it) }
        if (permissionsNotGranted.isEmpty()) {
            finish()
            return
        }
        ls.adapter = PermissionsListAdapter(
            this,
            permissionsNotGranted,
            onBtnGrantClicked
        )
    }

    private val onBtnGrantClicked: (androidPermission: AndroidPermission) -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && it.permission == Manifest.permission.MANAGE_EXTERNAL_STORAGE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri: Uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            managerStoragePermissionLauncher.launch(intent)
        } else {
            genericPermissionLauncher.launch(arrayOf(it.permission))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.permissionRequiredTitle)

        val permissionStrings = intent.getStringArrayListExtra("permission_strings")
        val titleInts = intent.getIntArrayExtra("title_ints")
        val textInts = intent.getIntArrayExtra("text_ints")

        if (permissionStrings != null && titleInts != null && textInts != null &&
            permissionStrings.size == titleInts.size && permissionStrings.size == textInts.size
        ) {
            allPermissions = permissionStrings.indices.map { i ->
                AndroidPermission(permissionStrings[i], titleInts[i], textInts[i])
            }
        } else {
            val json = intent.getStringExtra(PERMISSIONS_PAYLOAD)
            allPermissions = if (json != null) {
                try {
                    val payload =
                        SerializableEntityDeserializer.deserialize(json) as? PermissionsPayload
                    payload?.androidPermissions?.get() ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

        ls = findViewById(R.id.ls)
        ls.layoutManager = LinearLayoutManager(this)
        updateList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}