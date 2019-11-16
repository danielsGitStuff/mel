package de.mel.filesync.bash

import de.mel.Lok
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.DefaultFileConfiguration
import de.mel.auth.file.StandardFile
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Created by xor on 7/24/17.
 */

class BashToolsAndroidJavaImpl : BashToolsUnix() {
    override fun stuffModifiedAfter(directory: StandardFile, pruneDir: StandardFile, timeStamp: Long): AutoKlausIterator<StandardFile> = AutoKlausIterator.EmptyAutoKlausIterator()


    @Throws(IOException::class)
    override fun getFsBashDetails(file: StandardFile): FsBashDetails? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun rmRf(directory: StandardFile) {
        Lok.error("NOT:IMPLEMENTED")
    }


    @Throws(IOException::class)
    override fun find(directory: StandardFile, pruneDir: StandardFile): AutoKlausIterator<StandardFile> {
        val fileStack = Stack<Iterator<StandardFile>>()
        val prunePath = pruneDir.absolutePath
        Lok.debug("BashToolsAndroidJavaImpl.find.prune: $prunePath")
        fileStack.push(Arrays.asList<StandardFile>(*directory.listContent()).iterator())
        return object : AutoKlausIterator<StandardFile> {
            override fun close() {

            }

            internal var nextLine: String? = null

            private fun fastForward() {
                var iterator: Iterator<StandardFile>? = fileStack.peek()
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
                //inc();
                if (nextLine != null) {
                    if (nextLine!!.startsWith(prunePath)) {
                        nextLine = null
                        fastForward()
                        return nextLine != null
                    }
                    //dec();
                    return true
                } else {
                    try {
                        val iterator = fileStack.peek()
                        if (iterator.hasNext()) {
                            val nextFile = iterator.next()
                            if (nextFile.isDirectory)
                                fileStack.push(Arrays.asList<StandardFile>(*nextFile.listContent()).iterator())
                            nextLine = nextFile.absolutePath
                            return if (nextLine!!.startsWith(prunePath)) {
                                hasNext()
                            } else true
                            //dec();
                        } else {
                            fileStack.pop()
                            return if (fileStack.size == 0) {
                                //dec();
                                false
                            } else hasNext()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return false
                    }

                }
            }

            //            private void dec() {
            //                depth--;
            //            }
            //
            //            private void inc() {
            //                depth++;
            //                if (depth > max)
            //                    max = depth;
            //            }

            override fun next(): StandardFile {
                if (nextLine != null || hasNext()) {
                    val line = nextLine
                    nextLine = null
                    return AbstractFile.instance(line!!) as StandardFile
                } else {
                    throw NoSuchElementException()
                }
            }
        }
    }

    override fun isSymLink(f: StandardFile): Boolean = false


    override fun getContentFsBashDetails(file: StandardFile): MutableMap<String, FsBashDetails> {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        return mutableMapOf()
    }

    override fun lnS(file: StandardFile, target: String) {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        System.exit(-1)
    }

    companion object {

        //public static int max;
        //public static int depth = 0;

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            AbstractFile.configure(DefaultFileConfiguration())
            val dir = AbstractFile.instance("bash.test") as StandardFile
            val prune = AbstractFile.instance(dir.absolutePath + File.separator + "prune") as StandardFile
            val file = File(dir.absolutePath + File.separator + "file")
            dir.mkdirs()
            prune.mkdirs()
            for (i in 0..2099) {
                File(prune.absolutePath + File.separator + i).createNewFile()
            }
            file.createNewFile()
            val bashToolsAndroidJavaImpl = BashToolsAndroidJavaImpl()
            val iterator = bashToolsAndroidJavaImpl.find(dir, prune)
            while (iterator.hasNext())
                Lok.debug("BashToolsAndroidJavaImpl.main: " + iterator.next())
            //Lok.debug("BashToolsAndroidJavaImpl.main.max: " + max);
        }
    }
}
