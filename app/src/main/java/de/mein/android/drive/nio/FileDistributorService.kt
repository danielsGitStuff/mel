package de.mein.android.drive.nio

import android.app.IntentService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import de.mein.android.service.AndroidService
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mein.drive.nio.FileDistributionTask

class FileDistributorService : IntentService("FileDistributorService") {

    companion object {
        val TASK = "task"
        val BUFFER_SIZE = 1024 * 64
    }

    var androidService: AndroidService? = null
    var serviceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName,
                                        binder: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val localBinder = binder as AndroidService.LocalBinder
            androidService = localBinder.service
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            androidService = null
        }
    }

    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
        val intent = Intent(baseContext, AndroidService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return true
    }

    lateinit var distributionTask: FileDistributionTask
    override fun onHandleIntent(intent: Intent) {
        val json = intent.getStringExtra(TASK)
        distributionTask = (SerializableEntityDeserializer.deserialize(json) as FileDistributionTask?)!!
        distributionTask.initFromPaths()



    }


//    fun bla(): Unit {
//        val move = intent!!.getBooleanExtra(MOVE, false)
//        val srcPath = intent!!.getStringExtra(SRC_PATH)
//        val targetPath = intent!!.getStringExtra(TRGT_PATH)
//        val src = JFile(srcPath)
//        val target = JFile(targetPath)
//        val msg = (if (move) "moving" else "copying") + " '" + srcPath + "' -> '" + targetPath + "'"
//        Log.d(javaClass.simpleName, msg)
//        val fis = NWrap<InputStream>(null)
//        val fos = NWrap<OutputStream>(null)
//        try {
//            val srcDoc = src.createDocFile()
//            var targetDoc: DocumentFile? = target.createDocFile()
//            if (targetDoc == null) {
//                val targetParentDoc = target.createParentDocFile()
//                        ?: throw FileNotFoundException("directory does not exist: $targetPath")
//                val jtarget = JFile(target)
//                jtarget.createNewFile()
//                targetDoc = target.createDocFile()
//            }
//            val resolver = Tools.getApplicationContext().contentResolver
//            fis.v = resolver.openInputStream(srcDoc.getUri())
//            fos.v = resolver.openOutputStream(targetDoc.uri)
//
//            copyStream(fis.v, fos.v)
//            if (move) {
//                srcDoc.delete()
//            }
//        } catch (e: SAFAccessor.SAFException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        } finally {
//            N.s { fis.v.close() }
//            N.s { fos.v.close() }
//        }
//        Lok.debug("CopyService.onHandleIntent")
//    }
}