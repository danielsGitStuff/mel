package de.miniserver

import de.mein.Lok
import java.util.stream.Stream

class Processor( vararg command: String) {
    private var exitCode: Int? = null
    private var command = arrayOf(*command)
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

    internal var errorLines: Stream<String>? = null

    var process: Process? = null

    fun run(wait: Boolean = true, redirectOutput: ProcessBuilder.Redirect = ProcessBuilder.Redirect.PIPE, redirectError: ProcessBuilder.Redirect = ProcessBuilder.Redirect.PIPE): Processor {
        Lok.debug("bash: $cmdLine")
        process = ProcessBuilder(*command)
                .redirectOutput(redirectOutput)
                .redirectError(redirectError).start()
        if (wait)
            process?.waitFor()
        else
            return this
        exitCode = process?.exitValue()
        process!!.inputStream.bufferedReader().lines().forEach { println(it) }
        if (exitCode == 0) {
            Lok.debug("command '$cmdLine' succeeded")
            return this
        }
        Lok.error("command '$cmdLine' finished with $exitCode")
        errorLines = process!!.errorStream.bufferedReader().lines()
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
        process.inputStream.bufferedReader().lines().forEach { Lok.error(it) }
        process.errorStream.bufferedReader().lines().forEach { Lok.error(it) }
        return false
    }

    companion object {
        fun runProcesses(name: String, vararg processors: Processor) {
            processors.find { processor -> !processor.run().success }?.let {
                it.errorLines?.forEach { Lok.debug(it) }
                error("'$name' failed: ${it.cmdLine}")
            }
            Lok.debug("'$name' successful")

        }
    }

}