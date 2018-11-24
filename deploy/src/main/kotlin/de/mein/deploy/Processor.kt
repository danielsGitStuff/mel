package de.mein.deploy

import de.mein.Lok
import java.util.stream.Stream

class Processor(private vararg val command: String) {
    private var exitCode: Int? = null
    val cmdLine = command.fold("") { acc: String, s: String -> "$acc $s" }
    /**
     * whether or not the processed finished successfully. Runs the process if necessary
     */
    val success: Boolean
        get() {
            if (exitCode == null)
                run()
            return exitCode == 0
        }

    private var errorLines: Stream<String>? = null

    fun run(): Processor {
        val process = ProcessBuilder(command.asList())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE).start()
        process.waitFor()
        exitCode = process.exitValue()
        process.inputStream.bufferedReader().lines().forEach { println(it) }
        if (exitCode == 0) {
            Lok.debug("command '$cmdLine' succeeded")
            return this
        }
        Lok.error("command '$cmdLine' finished with $exitCode")
        errorLines = process.errorStream.bufferedReader().lines()
        return this
    }

    /**
     * returns whether or not the process finished successfully
     */
    fun processBool(vararg command: String): Boolean {
        val cmdLine = command.fold("") { acc: String, s: String -> "$acc $s" }
        val process = ProcessBuilder(command.asList())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE).start()
        process.waitFor()
        val exit = process.exitValue()
        process.inputStream.bufferedReader().lines().forEach { println(it) }
        if (exit == 0) {
            Lok.debug("command '$cmdLine' succeeded")
            return true
        }
        Lok.error("command '$cmdLine' finished with $exit")
        process.errorStream.bufferedReader().lines().forEach { Lok.error(it) }
        return false
    }


}