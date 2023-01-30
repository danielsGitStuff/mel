package de.mel.android.permissions

import android.os.Bundle
import android.view.MenuItem
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import de.mel.AndroidPermission
import de.mel.R
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer


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
    lateinit var permissionsPayload: PermissionsPayload
    lateinit var ls: ListView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val json = intent.getStringExtra(PERMISSIONS_PAYLOAD);
        permissionsPayload = SerializableEntityDeserializer.deserialize(json) as PermissionsPayload
        ls = findViewById<ListView>(R.id.ls)
        ls.adapter = PermissionsListAdapter(this, permissionsPayload.androidPermissions.repeat(3).get())
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