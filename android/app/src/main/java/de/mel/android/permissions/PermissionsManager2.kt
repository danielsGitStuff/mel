package de.mel.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContextCompat
import de.mel.AndroidPermission
import de.mel.auth.tools.N
// todo clean
class PermissionsManager2(
    private val context: Context, val permissions: List<AndroidPermission>
) {

    fun hasPermission(permission: AndroidPermission): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permission.isOfKind(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
            Environment.isExternalStorageManager()
        } else ContextCompat.checkSelfPermission(
            context, permission.permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions() = permissions.all { hasPermission(it) }

    fun startPermissionsActivity() {
        if (hasPermissions())
            return
        val bundle = Bundle()
        val payload = PermissionsPayload()
        permissions.forEach {
            payload.addEntry(
                PermissionsEntry().setPermissionString(it.permission).setTitle(it.titleInt)
                    .setText(it.textInt)
            )
        }
        val intent = Intent("de.mel.permissions.request")
        intent.putExtra(PermissionsActivity.PERMISSIONS_PAYLOAD, N.r<String> { payload.toJSON() })
        context.startActivity(intent, bundle)
    }
}