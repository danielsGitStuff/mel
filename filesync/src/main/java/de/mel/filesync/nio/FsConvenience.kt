package de.mel.filesync.nio

import de.mel.Lok
import de.mel.auth.file.IFile
import de.mel.auth.file.StandardFile
import de.mel.filesync.sql.FsEntry
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * What a file system can do for us on top of what every file system does, and applies it where it can: the service asks for the result, this finds the way.
 * Nothing may depend on it. Every operation says whether it worked, and a file system that can do less is slower or loses a cosmetic detail, not data.
 * <p>
 * One per service, because the service has one root and with it one file system (or at least one that matters: the transfer directory is inside the root,
 * so downloads and their final places are on the same one).
 */
interface FsConvenience {
    /**
     * Gives [file] the modified time [fsFile] says it has, as rsync -t does. The file system may not allow that, then the file keeps the time it has.
     * The caller stores what the file says afterwards, not what it asked for.
     *
     * @return true if the file has the time now
     */
    fun applyModified(file: IFile, fsFile: FsEntry?): Boolean

    /**
     * Puts a copy of [source] at [destination], which must not exist: a reference that costs no space if the file system has them, a physical copy if [physicalAllowed].
     * The copy has the modified time of the source.
     *
     * @throws IOException if it failed half way; a copy that was not made because it would have to be physical is [CopyResult.NOT_DONE]
     */
    @Throws(IOException::class)
    fun copy(source: IFile, destination: IFile, physicalAllowed: Boolean = true): CopyResult
}

enum class CopyResult {
    /** shares the data with the source, takes (almost) no space */
    REFERENCE,

    /** a copy of the data */
    PHYSICAL,

    /** nothing was made */
    NOT_DONE
}

/**
 * What works everywhere: the modified time as far as the file allows it, and physical copies.
 */
open class DefaultFsConvenience : FsConvenience {
    override fun applyModified(file: IFile, fsFile: FsEntry?): Boolean {
        val time = fsFile?.modified?.v() ?: return false
        return try {
            file.setLastModified(time)
        } catch (e: Exception) {
            Lok.debug("could not set the modification time of ${file.absolutePath}: $e")
            false
        }
    }

    @Throws(IOException::class)
    override fun copy(source: IFile, destination: IFile, physicalAllowed: Boolean): CopyResult {
        if (!physicalAllowed)
            return CopyResult.NOT_DONE
        physicalCopy(source, destination)
        return CopyResult.PHYSICAL
    }

    @Throws(IOException::class)
    protected fun physicalCopy(source: IFile, destination: IFile) {
        val ins = source.inputStream() ?: throw IOException("could not read from ${source.absolutePath}")
        try {
            val out = destination.writer() ?: throw IOException("could not write to ${destination.absolutePath}")
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var position = 0L
                var read = ins.read(buffer)
                while (read > 0) {
                    // the second argument is the position in the target file, not in the buffer
                    out.append(buffer, position, read)
                    position += read
                    read = ins.read(buffer)
                }
            } catch (e: Exception) {
                destination.delete()
                throw if (e is IOException) e else IOException(e)
            } finally {
                out.close()
            }
        } finally {
            ins.close()
        }
        source.lastModified()?.let { destination.setLastModified(it) }
    }

    companion object {
        const val BUFFER_SIZE = 1024 * 64
    }
}

/**
 * Where there is a `cp` that makes references (reflinks: btrfs, xfs, zfs...) it is used for files that are plain files on this machine.
 * Whether it works is found out by trying: `--reflink=always` fails if the file system cannot, and never makes a physical copy by itself.
 * After a failure for that reason it is not tried again, because a process per file is not free.
 */
open class UnixFsConvenience : DefaultFsConvenience() {
    @Volatile
    private var referencesWork = true

    @Throws(IOException::class)
    override fun copy(source: IFile, destination: IFile, physicalAllowed: Boolean): CopyResult {
        if (referencesWork && source is StandardFile && destination is StandardFile) {
            if (reference(source, destination))
                return CopyResult.REFERENCE
        }
        return super.copy(source, destination, physicalAllowed)
    }

    private fun reference(source: StandardFile, destination: StandardFile): Boolean {
        return try {
            val process = ProcessBuilder("cp", "--reflink=always", "--preserve=timestamps", "--", source.absolutePath, destination.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                File(destination.absolutePath).delete()
                return false
            }
            if (process.exitValue() == 0)
                return true
            // nothing of it may stay, a half made destination would be taken for the copy
            File(destination.absolutePath).delete()
            // "not supported" and "cross device" will be the same next time, a full disk or a vanished file will not
            if (output.contains("not supported", true) || output.contains("cross-device", true) || output.contains("invalid argument", true) || output.contains("unrecognized option", true) || output.contains("invalid option", true)) {
                Lok.debug("no references on this file system: $output")
                referencesWork = false
            }
            false
        } catch (e: IOException) {
            // no cp
            referencesWork = false
            false
        }
    }
}
