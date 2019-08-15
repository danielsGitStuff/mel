package de.mein.drive.bash

import java.io.File
import java.io.IOException
import java.util.*

import de.mein.Lok
import de.mein.auth.file.AFile
import de.mein.auth.file.DefaultFileConfiguration

/**
 * Created by xor on 7/24/17.
 */

class BashToolsAndroidJavaImpl : BashToolsImpl {
    override fun setBinPath(binPath: String) {

    }

    @Throws(IOException::class)
    override fun getINodesOfDirectory(file: AFile<*>): Set<Long>? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: AFile<*>): FsBashDetails? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun rmRf(directory: AFile<*>) {
        Lok.error("NOT:IMPLEMENTED")
    }

    @Throws(IOException::class, BashToolsException::class)
    override fun stuffModifiedAfter(referenceFile: AFile<*>, directory: AFile<*>, pruneDir: AFile<*>): List<AFile<*>> {
        val details = BashTools.getFsBashDetails(referenceFile)
        val time = details.modified
        val dir = File(directory.absolutePath)
        val list = mutableListOf<AFile<*>>()
        if (dir.exists())
            dir.walkTopDown().onEnter { it.absolutePath != pruneDir.absolutePath }.filter { it.lastModified() >= time }.forEach {
                list.add(AFile.instance(it))
            }
        return list
    }

    @Throws(IOException::class)
    override fun find(directory: AFile<*>, pruneDir: AFile<*>): AutoKlausIterator<AFile<*>> {
        val fileStack = Stack<Iterator<AFile<*>>>()
        val prunePath = pruneDir.absolutePath
        Lok.debug("BashToolsAndroidJavaImpl.find.prune: $prunePath")
        fileStack.push(Arrays.asList<AFile<*>>(*directory.listContent()).iterator())
        return object : AutoKlausIterator<AFile<*>> {
            override fun close() {

            }

            internal var nextLine: String? = null

            private fun fastForward() {
                var iterator: Iterator<AFile<*>>? = fileStack.peek()
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
                                fileStack.push(Arrays.asList<AFile<*>>(*nextFile.listContent()).iterator())
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

            override fun next(): AFile<*> {
                if (nextLine != null || hasNext()) {
                    val line = nextLine
                    nextLine = null
                    return AFile.instance(line)
                } else {
                    throw NoSuchElementException()
                }
            }
        }
    }


    @Throws(IOException::class, InterruptedException::class)
    override fun stuffModifiedAfter(originalFile: AFile<*>, pruneDir: AFile<*>, timeStamp: Long): AutoKlausIterator<AFile<*>>? {
        Lok.error("NOT:IMPLEMENTED")
        return null
    }

    @Throws(IOException::class)
    override fun mkdir(dir: AFile<*>) {
        val i = 0
        while (!dir.exists()) {
            dir.mkdirs()
            Lok.debug("BashToolsAndroidJavaImpl.mkdir.$i")
        }
    }

    @Throws(IOException::class)
    override fun mv(source: File, target: File): Boolean {
        System.err.println("BashToolsAndroidJavaImpl.mv.NOT:IMPLEMENTED")
        return false
    }

    override fun isSymLink(f: AFile<*>): Boolean {
        return false
    }

    override fun getContentFsBashDetails(file: AFile<*>): Map<String, FsBashDetails>? {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        return null
    }

    override fun lnS(file: AFile<*>, target: String) {
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
            AFile.configure(DefaultFileConfiguration())
            val dir = AFile.instance("bash.test")
            val prune = AFile.instance(dir.absolutePath + File.separator + "prune")
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
