package de.mel.android.filesync.bash

import java.io.File
import java.io.IOException
import java.util.*

import de.mel.Lok
import de.mel.android.file.AndroidFile
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.DefaultFileConfiguration
import de.mel.filesync.bash.AutoKlausIterator
import de.mel.filesync.bash.BashTools
import de.mel.filesync.bash.BashToolsException
import de.mel.filesync.bash.FsBashDetails

/**
 * Created by xor on 7/24/17.
 */

class BashToolsAndroidSAFImpl : BashTools<AndroidFile>() {


    override fun setBinPath(binPath: String) {

    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: AndroidFile): FsBashDetails? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun rmRf(directory: AndroidFile) {
        Lok.error("NOT:IMPLEMENTED")
    }


    @Throws(IOException::class)
    override fun find(directory: AndroidFile, pruneDir: AndroidFile): AutoKlausIterator<AndroidFile> {
        val fileStack = Stack<Iterator<AndroidFile>>()
        val prunePath = pruneDir.absolutePath
        Lok.debug("BashToolsAndroidJavaImpl.find.prune: $prunePath")
        fileStack.push((directory.listContent()?.iterator() ?: emptyList<AndroidFile>().iterator()))
        return object : AutoKlausIterator<AndroidFile> {
            override fun close() {

            }

            internal var nextLine: String? = null

            private fun fastForward() {
                var iterator: Iterator<AndroidFile>? = fileStack.peek()
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
                                fileStack.push(nextFile.listContent()?.iterator() ?: emptyList<AndroidFile>().iterator())
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

            override fun next(): AndroidFile {
                if (nextLine != null || hasNext()) {
                    val line = nextLine
                    nextLine = null
                    return AbstractFile.instance(line!!) as AndroidFile
                } else {
                    throw NoSuchElementException()
                }
            }
        }
    }



    override fun isSymLink(f: AndroidFile): Boolean {
        return false
    }


    override fun lnS(file: AndroidFile, target: String) {
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
            val dir = AbstractFile.instance("bash.test") as AndroidFile
            val prune = AbstractFile.instance(dir.absolutePath + File.separator + "prune") as AndroidFile
            val file = File(dir.absolutePath + File.separator + "file")
            dir.mkdirs()
            prune.mkdirs()
            for (i in 0..2099) {
                File(prune.absolutePath + File.separator + i).createNewFile()
            }
            file.createNewFile()
            val bashToolsAndroidJavaImpl = BashToolsAndroidSAFImpl()
            val iterator = bashToolsAndroidJavaImpl.find(dir, prune)
            while (iterator.hasNext())
                Lok.debug("BashToolsAndroidJavaImpl.main: " + iterator.next())
            //Lok.debug("BashToolsAndroidJavaImpl.main.max: " + max);
        }
    }

    override fun stuffModifiedAfter(referenceFile: AndroidFile, directory: AndroidFile, pruneDir: AndroidFile): List<AndroidFile> {
        throw BashToolsException.NotImplemented()
    }

    override fun stuffModifiedAfter(directory: AndroidFile, pruneDir: AndroidFile, timeStamp: Long): AutoKlausIterator<AndroidFile> {
        throw BashToolsException.NotImplemented()
    }

    override fun getContentFsBashDetails(directory: AndroidFile): MutableMap<String, FsBashDetails> {
        throw NotImplementedError()
    }
}
