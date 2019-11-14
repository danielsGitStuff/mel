package de.mel.filesync.bash

import java.io.File
import java.io.IOException
import java.util.*

import de.mel.Lok
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.DefaultFileConfiguration

/**
 * Created by xor on 7/24/17.
 */

class BashToolsAndroidJavaImpl : BashToolsImpl() {


    override fun setBinPath(binPath: String) {

    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: AbstractFile): FsBashDetails? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun rmRf(directory: AbstractFile) {
        Lok.error("NOT:IMPLEMENTED")
    }



    @Throws(IOException::class)
    override fun find(directory: AbstractFile, pruneDir: AbstractFile): AutoKlausIterator<AbstractFile> {
        val fileStack = Stack<Iterator<AbstractFile>>()
        val prunePath = pruneDir.absolutePath
        Lok.debug("BashToolsAndroidJavaImpl.find.prune: $prunePath")
        fileStack.push(Arrays.asList<AbstractFile>(*directory.listContent()).iterator())
        return object : AutoKlausIterator<AbstractFile> {
            override fun close() {

            }

            internal var nextLine: String? = null

            private fun fastForward() {
                var iterator: Iterator<AbstractFile>? = fileStack.peek()
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

            override fun remove() {

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
                                fileStack.push(Arrays.asList<AbstractFile>(*nextFile.listContent()).iterator())
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

            override fun next(): AbstractFile {
                if (nextLine != null || hasNext()) {
                    val line = nextLine
                    nextLine = null
                    return AbstractFile.instance(line)
                } else {
                    throw NoSuchElementException()
                }
            }
        }
    }



    @Throws(IOException::class)
    override fun mkdir(dir: AbstractFile) {
        val i = 0
        while (!dir.exists()) {
            dir.mkdirs()
            Lok.debug("BashToolsAndroidJavaImpl.mkdir.$i")
        }
    }


    override fun isSymLink(f: AbstractFile): Boolean {
        return false
    }

    override fun getContentFsBashDetails(file: AbstractFile): Map<String, FsBashDetails> {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        return mapOf()
    }

    override fun lnS(file: AbstractFile, target: String) {
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
            val dir = AbstractFile.instance("bash.test")
            val prune = AbstractFile.instance(dir.absolutePath + File.separator + "prune")
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
