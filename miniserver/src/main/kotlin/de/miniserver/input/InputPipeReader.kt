package de.miniserver.input

import de.mein.Lok
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class InputPipeReader private constructor(val fileName: String) {


    init {
        val file = File(fileName)
        if (file.exists())
            file.delete()

        /// build pipe
        ProcessBuilder("mkfifo", fileName).start().waitFor()
        Lok.debug("pipe created ${file.absolutePath}")

        GlobalScope.launch {
            //build cat
            val b = ProcessBuilder("cat", fileName)
            b.redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
            val process = b.start()
            Lok.debug("cat started on ${file.absolutePath}")
            val input = process.inputStream.read()
            Lok.debug("stopping, read: $input")
            System.exit(0)
        }

    }

    companion object {
        const val STOP_FILE_NAME = "stop.input"
        fun create(fileName: String): InputPipeReader {
            return InputPipeReader(fileName)
        }
    }
}