package de.mel.auth.file

import de.mel.Lok
import java.io.File

class DefaultFileConfiguration : AbstractFile.Configuration<StandardFile>() {
    override fun instance(path: String): StandardFile {
        return StandardFile(path)
    }

    override fun separator(): String {
        return File.separator
    }

    override fun instance(file: File): StandardFile {
        return StandardFile(file)
    }

    override fun instance(parent: StandardFile, name: String): StandardFile {
        return StandardFile(parent, name)
    }

    override fun instance(originalFile: StandardFile): StandardFile {
        return StandardFile((originalFile as StandardFile?)!!)
    }
}