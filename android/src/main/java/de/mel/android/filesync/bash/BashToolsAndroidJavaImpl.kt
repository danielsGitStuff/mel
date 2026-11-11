package de.mel.android.filesync.bash

import java.io.File
import java.io.IOException
import java.util.*

import de.mel.Lok
import de.mel.filesync.bash.AutoKlausIterator
import de.mel.filesync.bash.BashTools
import de.mel.filesync.bash.BashToolsException
import de.mel.filesync.bash.FsBashDetails

/**
 * Created by xor on 7/24/17.
 */

class BashToolsAndroidJavaImpl : BashTools() {

    override fun setBinPath(binPath: String) {

    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: File): FsBashDetails? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun rmRf(directory: File) {
        Lok.error("NOT:IMPLEMENTED")
    }

    @Throws(IOException::class)
    override fun find(directory: File, pruneDir: File): AutoKlausIterator<File> {
        val fileStack = Stack<Iterator<File>>()
        val prunePath = pruneDir.absolutePath
        Lok.debug("BashToolsAndroidJavaImpl.find.prune: $prunePath")
        fileStack.push(directory.listFiles()?.iterator() ?: emptyList<File>().iterator())
        return object : AutoKlausIterator<File> {
            override fun close() {

            }

            internal var nextLine: String? = null

            private fun fastForward() {
                var iterator: Iterator<File>? = fileStack.peek()
                while (iterator != null) {
                    while (iterator.hasNext()) {
                        val f = iterator.next()
                        if (!f.absolutePath.startsWith(prunePath)) {
                            nextLine = f.absolutePath
                            return
                        }
                    }
                    fileStack.pop()
                    if (fileStack.size != 0) {
                        iterator = fileStack.peek()
                    } else
                        return
                }
            }


            override fun hasNext(): Boolean {
                if (nextLine != null) {
                    if (nextLine!!.startsWith(prunePath)) {
                        nextLine = null
                        fastForward()
                        return nextLine != null
                    }
                    return true
                } else {
                    try {
                        val iterator = fileStack.peek()
                        if (iterator.hasNext()) {
                            val nextFile = iterator.next()
                            if (nextFile.isDirectory)
                                fileStack.push(nextFile.listFiles()?.iterator() ?: emptyList<File>().iterator())
                            nextLine = nextFile.absolutePath
                            return if (nextLine!!.startsWith(prunePath)) {
                                hasNext()
                            } else true
                        } else {
                            fileStack.pop()
                            return if (fileStack.size == 0) {
                                false
                            } else hasNext()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return false
                    }

                }
            }

            override fun next(): File {
                if (nextLine != null || hasNext()) {
                    val line = nextLine
                    nextLine = null
                    return File(line!!)
                } else {
                    throw NoSuchElementException()
                }
            }
        }
    }

    override fun isSymLink(f: File): Boolean {
        return false
    }

    override fun lnS(file: File, target: String) {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        System.exit(-1)
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val dir = File("bash.test")
            val prune = File(dir, "prune")
            val file = File(dir, "file")
            dir.mkdirs()
            prune.mkdirs()
            for (i in 0..2099) {
                File(prune, i.toString()).createNewFile()
            }
            file.createNewFile()
            val bashToolsAndroidJavaImpl = BashToolsAndroidJavaImpl()
            val iterator = bashToolsAndroidJavaImpl.find(dir, prune)
            while (iterator.hasNext())
                Lok.debug("BashToolsAndroidJavaImpl.main: " + iterator.next())
        }
    }

    override fun stuffModifiedAfter(referenceFile: File, directory: File, pruneDir: File): List<File> {
        throw BashToolsException.NotImplemented()
    }

    override fun stuffModifiedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File> {
        throw BashToolsException.NotImplemented()
    }

    override fun getContentFsBashDetails(directory: File): MutableMap<String, FsBashDetails> {
        throw NotImplementedError()
    }
}
