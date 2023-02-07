package de.mel.android.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import de.mel.AndroidPermission
import de.mel.R
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer

// todo clean
class PermissionsActivity : AppCompatActivity() {

    companion object {
        const val PERMISSIONS_PAYLOAD = "permissionsPayload"
    }

    // Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher. You can use either a val, as shown in this snippet,
// or a lateinit var in your onAttach() or onCreate() method.
    val genericPermissionLauncher =
        registerForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.

            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }
    val g =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
            permissionsMap.forEach { entry ->
                if (entry.value) permissionsGranted.add(entry.key) else permissionsGranted.remove(
                    entry.key
                )
            }
            ls.adapter = PermissionsListAdapter(
                this,
                permissionsPayload.androidPermissions.filter { !permissionsGranted.contains(it.permission) }
                    .get(), onBtnGrantClicked
            )
        }
    val gSTORAGE = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            permissionsGranted.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        } else {
            permissionsGranted.remove(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        }
        updateList()
    }
    lateinit var permissionsPayload: PermissionsPayload
    lateinit var ls: ListView
    private val permissionsGranted = mutableSetOf<String>()

    private fun updateList() {
        val pm = PermissionsManager2(this, emptyList())
        val permissionsNotGranted =
            permissionsPayload.androidPermissions.filter { !pm.hasPermission(it) }
        if (permissionsNotGranted.isEmpty)
            finish()
        ls.adapter = PermissionsListAdapter(
            this,
            permissionsNotGranted.get(),
            onBtnGrantClicked
        )
    }

    private val onBtnGrantClicked: (androidPermission: AndroidPermission) -> Unit = {
        when (it.permission) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
//                g.launch(arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE))
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri: Uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
//                    startActivity(intent)
                gSTORAGE.launch(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val json = intent.getStringExtra(PERMISSIONS_PAYLOAD);
        permissionsPayload = SerializableEntityDeserializer.deserialize(json) as PermissionsPayload
        ls = findViewById<ListView>(R.id.ls)
        updateList()
        permissionsPayload.androidPermissions.forEach { println("--- ${it.permission}") }
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