package de.mel.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.navigation.NavigationView
import de.mel.AndroidPermission
import de.mel.Lok
import de.mel.PermissionsManager
import de.mel.android.service.AndroidService
import de.mel.android.service.AndroidService.LocalBinder
import de.mel.auth.MelStrings
import de.mel.auth.service.MelAuthService
import org.jdeferred.Deferred
import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject
import java.security.SecureRandom

import de.mel.R

/**
 * Created by xor on 03.08.2017.
 */
abstract class MelActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    protected var lastActivityResult: ActivityResult? = null
    protected var activityLauncher: ActivityResultLauncher<Intent>? = null
    public var androidService: AndroidService? = null
        protected set

    init {
        this.activityLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()
            ) {
                println("DEBUG RESULT")
                this.lastActivityResult = it
                if (it.resultCode == Activity.RESULT_OK) {
                    val requestCode =
                        it.data!!.getIntExtra(MelStrings.Activity.SOURCE_REQUEST_ID, -1);
//                    val launchResult = launchResultMap.remove(requestCode)
                    MelActivity.Companion.launchPayloads.remove(requestCode)
                    val launchResult = this.launchResult
                    this.launchResult = null
                    launchResult?.onResultReceived(it.resultCode, it.data)
                } else {
                    launchResult = null
                }
            }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected var serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            binder: IBinder
        ) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val localBinder = binder as LocalBinder
            androidService = localBinder.service
            Lok.debug(".onServiceConnected: " + androidService.toString())
            androidService?.startedPromise
                ?.done { result: MelAuthService? ->
                    onAndroidServiceAvailable(
                        androidService
                    )
                }
                ?.fail { obj: Exception -> obj.printStackTrace() }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            androidService = null
        }
    }
    private val permissionManagers = mutableMapOf<Int, PermissionsManager>()

    //    private val launchResultMap: MutableMap<Int, MelActivityLaunchResult> = HashMap()
    protected var launchResult: MelActivityLaunchResult? = null
    fun launchActivityForResult(
        launchIntent: Intent,
        melActivityLaunchResult: MelActivityLaunchResult
    ) {
        val id = Tools.generateIntentRequestCode()
        launchIntent.putExtra(MelStrings.Notifications.REQUEST_CODE, id)
        this.launchResult = melActivityLaunchResult
//        launchResultMap[id] = melActivityLaunchResult
        startActivityForResult(launchIntent, id)
    }

    fun launchActivityForResult(
        launchIntent: Intent,
        melActivityLaunchResult: MelActivityLaunchResult,
        vararg payloads: MelActivityPayload<*>
    ) {
        val id = Tools.generateIntentRequestCode()
//        launchResultMap[id] = melActivityLaunchResult
        this.launchResult = melActivityLaunchResult
        launchIntent.putExtra(MelStrings.Notifications.REQUEST_CODE, id)
        launchPayloads[id] = payloads.toMutableList()
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
//            ActivityResultCallback {
//                println("DEBUG RESULT")
//            }).launch(launchIntent)
        activityLauncher!!.launch(launchIntent)
//        startActivityForResult(launchIntent, id)
    }
    /**
     * show a simple dialog where the user can only click ok
     *
     * @param title
     * @param message
     */
    /**
     * show a simple dialog where the user can only click ok
     *
     * @param title
     * @param message
     */
    @JvmOverloads
    fun showMessage(title: Int, message: Int, whenDone: Runnable? = null) {
        runOnUiThread {
            val builder =
                AlertDialog.Builder(this)
            builder.setMessage(message)
                .setTitle(title)
                .setPositiveButton(R.string.btnOk) { dialog, which ->
                    whenDone?.run()
                }
            val alertDialog = builder.create()
            alertDialog.show()
        }
    }

    override fun onDestroy() {
        androidService = null
        super.onDestroy()
    }

    protected fun bindService() {
        val intent = Intent(baseContext, AndroidService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    protected open fun onAndroidServiceAvailable(androidService: AndroidService?) {
        this.androidService = androidService
    }

    fun requestPermission(
        permissionsManager: PermissionsManager,
        permission: AndroidPermission
    ) {
    }

    fun registerPermissionManager(requestCode: Int, permissionsManager: PermissionsManager) {
        this.permissionManagers[requestCode] = permissionsManager
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val deniedPermissions: MutableList<String> = ArrayList()
        for (i in permissions.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permissions[i])
            }
        }
//        val permissionsManager = permissionManagers[requestCode]!!
//        if (deniedPermissions.size == 0) {
//            permissionsManager
//        } else {
//            deferred.reject(deniedPermissions)
//        }
    }

    fun hasPermission(permission: String?): Boolean {
        val result = ContextCompat.checkSelfPermission(this, permission!!)
        return result == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions(vararg permissions: String?): Boolean {
        for (permission in permissions) {
            if (!hasPermission(permission)) return false
        }
        return true
    }

    /**
     * annoys the user one time with each given permission.
     *
     * @param permissions
     * @return Promise that resolves when all permission have been granted or will reject with all denied permissions.
     */
    @Deprecated("")
    fun annoyWithPermissions(vararg permissions: String): Promise<Void?, List<String>, Void> {
        val deferred: Deferred<Void?, List<String>, Void> = DeferredObject()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            var request = false
            for (permission in permissions) {
                val result = ContextCompat.checkSelfPermission(this, permission)
                if (result != PackageManager.PERMISSION_GRANTED) {
                    request = true
                    break
                }
            }
            var id = SecureRandom().nextInt(65536)
            id = if (id > 0) id else -1 * id //make positive
            if (request) {
//                permissionManagers.append(id, deferred)
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    id
                )
            } else {
                deferred.resolve(null)
            }
            Lok.debug(".askForPermission()?: $request")
        } else {
            deferred.resolve(null)
        }
        return deferred
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        val launchResult = launchResultMap.remove(requestCode)
//        MelActivity.Companion.launchPayloads.remove(requestCode)
//        launchResult?.onResultReceived(resultCode, data)
//    }

    interface MelActivityLaunchResult {
        fun onResultReceived(resultCode: Int, result: Intent?)
    }

    companion object {
        @Deprecated(message = "use android methods instead?")
        private val launchPayloads = mutableMapOf<Int, MutableList<MelActivityPayload<*>>>()
        fun onLaunchDestroyed(requestCode: Int?) {
            MelActivity.Companion.launchPayloads.remove(requestCode)
        }

        fun getLaunchPayloads(requestCode: Int?): List<MelActivityPayload<*>> {
            return MelActivity.Companion.launchPayloads.get(requestCode)!!.toList()
        }

        /**
         * annoys the user one time with each given permission.
         *
         * @param permissions
         * @return Promise that resolves when all permission have been granted or will reject with all denied permissions.
         */
        fun annoyWithPermissions(activity: Activity, vararg permissions: String): Promise<Void?, List<String>, Void> {
            val deferred: Deferred<Void?, List<String>, Void> = DeferredObject()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                var request = false
                for (permission in permissions) {
                    val result = ContextCompat.checkSelfPermission(activity, permission)
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        request = true
                        break
                    }
                }
                var id = SecureRandom().nextInt(65536)
                id = if (id > 0) id else -1 * id //make positive
                if (request) {
//                permissionManagers.append(id, deferred)
                    ActivityCompat.requestPermissions(
                        activity,
                        permissions,
                        id
                    )
                } else {
                    deferred.resolve(null)
                }
                Lok.debug(".askForPermission()?: $request")
            } else {
                deferred.resolve(null)
            }
            return deferred
        }
    }
}