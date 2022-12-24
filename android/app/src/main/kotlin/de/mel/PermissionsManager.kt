package de.mel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import de.mel.android.MelActivity
import kotlinx.coroutines.Runnable
import org.jdeferred.impl.DeferredObject
import java.security.SecureRandom
import kotlin.math.abs


class PermissionsManager(private val melActivity: MelActivity) {

    private val permissions = mutableListOf<AndroidPermission>()
    private var onSuccess: Runnable? = null
    private var onFail: Runnable? = null
//    var manageExternalStoragePermission: AndroidPermission? = null


    fun addPermission(androidPermission: AndroidPermission): PermissionsManager {
//        if (androidPermission.isOfKind(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
//            manageExternalStoragePermission = androidPermission
//        } else {
        permissions.add(androidPermission)
//        }
        return this
    }

    private fun hasPermission(permission: AndroidPermission): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permission.isOfKind(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
            Environment.isExternalStorageManager()
        } else
            ContextCompat.checkSelfPermission(
                melActivity,
                permission.permission
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions() =
        permissions.all { hasPermission(it) }


    fun doIt(): Unit {
        val d = DeferredObject<Void, Exception, Void>()
        permissions.filter { p -> !hasPermission(p) }
            .forEach { permission ->
                val builder = AlertDialog.Builder(melActivity)
                builder.setMessage(permission.textInt).setTitle(permission.titleInt)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permission.isOfKind(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
                    // todo start activity and so on
                    println("bla debug 59kg")
                    builder.setPositiveButton(R.string.btnOk) { dialog, which ->
                        run {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            val uri: Uri = Uri.fromParts("package", melActivity.packageName, null)
                            intent.data = uri
                            melActivity.startActivity(intent)
                        }
                    }

                } else {
//                    val builder = AlertDialog.Builder(melActivity)
//                    builder.setMessage(permission.textInt).setTitle(permission.titleInt)
                    builder.setPositiveButton(R.string.btnOk) { dialog, which ->
                        run {
                            val id = abs(SecureRandom().nextInt(65536))
                            melActivity.registerPermissionManager(id, this)
                            ActivityCompat.requestPermissions(
                                melActivity,
                                arrayOf(permission.permission),
                                id
                            )
                        }
                    }
                }
                builder.create().show()
            }
//        if (manageExternalStoragePermission != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            SAFAccessor.canManageAll()
//        }
    }

    fun onSuccess(onSuccess: Runnable): PermissionsManager {
        this.onSuccess = onSuccess
        return this
    }

    fun onFail(onFail: Runnable): PermissionsManager {
        this.onFail = onFail
        return this
    }

    fun execute() {

    }
}