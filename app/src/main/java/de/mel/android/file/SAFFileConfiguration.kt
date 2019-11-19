package de.mel.android.file

import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import java.io.File

class SAFFileConfiguration : AbstractFile.Configuration() {
    override fun instance(path: String): AbstractFile<IFile> = SAFFile(path) as AbstractFile<IFile>

    override fun instance(file: File): AbstractFile<IFile> = SAFFile(file.absolutePath) as AbstractFile<IFile>

    override fun instance(parent: AbstractFile<IFile>, name: String): AbstractFile<IFile> = SAFFile(parent,name) as AbstractFile<IFile>

    override fun instance(originalFile: AbstractFile<IFile>): AbstractFile<IFile> = SAFFile(originalFile) as AbstractFile<IFile>

    override fun separator(): String = "This separator is useless"
}