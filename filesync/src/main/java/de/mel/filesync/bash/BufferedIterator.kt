package de.mel.filesync.bash

import java.io.BufferedReader
import java.io.File
import java.io.Reader
import java.util.*

/**
 * Created by xor on 7/24/17.
 */
abstract class BufferedIterator<T>(ins: Reader) : BufferedReader(ins), AutoKlausIterator<T> {
    abstract fun convert(line: String): T
    internal var nextLine: String? = null
    override fun hasNext(): Boolean {
        return if (nextLine != null) {
            true
        } else {
            try {
                nextLine = readLine()
                nextLine != null
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun next(): T {
        if (nextLine != null || hasNext()) {
            val line = nextLine!!
            nextLine = null
            return convert(line)
        } else {
            throw NoSuchElementException()
        }
    }

    class BufferedFileIterator(ins: Reader) : BufferedIterator<File>(ins) {
        override fun convert(line: String): File {
            return File(line)
        }
    }

    class BufferedStringIterator(ins: Reader) : BufferedIterator<String>(ins) {
        override fun convert(line: String): String {
            return line
        }
    }
}
