package de.web.miniserver.input

import de.mel.Lok
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

class InputPipeReader private constructor(val workingDirectory: File, val fileName: String) {
    fun stop() {
        try {
            process.destroyForcibly()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            pipeFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    private lateinit var process: Process

    private var pipeFile: File = File(workingDirectory, fileName)

    init {
        if (pipeFile.exists())
            pipeFile.delete()

        /// build pipe
        ProcessBuilder("mkfifo", pipeFile.absolutePath).start().waitFor()
        Lok.info("pipe created ${pipeFile.absolutePath}")

        GlobalScope.launch {
            //build cat
            val b = ProcessBuilder("cat", pipeFile.absolutePath)
            b.redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
            process = b.start()
            Lok.info("cat started on ${pipeFile.absolutePath}")

            var input: Int = 0
            while (input == 0) {
                input = process.inputStream.available()
                delay(1000)
            }
            Lok.info("stopping, read: $input")
//            if (input > 0)
            exitProcess(0)
        }

    }

    companion object {
        const val STOP_FILE_NAME = "stop.input"
        fun create(workingDirectory: File, fileName: String): InputPipeReader {
            return InputPipeReader(workingDirectory, fileName)
        }
    }
}