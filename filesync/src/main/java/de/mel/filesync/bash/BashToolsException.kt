package de.mel.filesync.bash

import de.mel.filesync.bash.BufferedIterator.BufferedStringIterator
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

/**
 * Created by xor on 5/24/17.
 */
open class BashToolsException : IOException {
    /**
     * As I learned today there is an equivalent class built into kotlin.
     */
    class NotImplemented : BashToolsException("NOT:IMPLEMENTED")

    private val lines: MutableList<String> = ArrayList()

    constructor(lines: Iterator<String>) {
        readLines(lines)
    }

    constructor(line: String) {
        lines.add(line)
    }

    private fun readLines(iterator: Iterator<String>) {
        while (iterator.hasNext()) lines.add(iterator.next())
    }

    constructor(proc: Process) {
        val lines: Iterator<String> = BufferedStringIterator(InputStreamReader(proc.errorStream))
        readLines(lines)
    }

    override fun printStackTrace() {
        System.err.println(toString())
        super.printStackTrace()
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder(javaClass.simpleName + "\n")
        for (line in lines) stringBuilder.append(line).append("\n")
        return stringBuilder.toString()
    }
}