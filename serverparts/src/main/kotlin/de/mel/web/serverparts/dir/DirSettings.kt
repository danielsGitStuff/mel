package de.mel.web.serverparts.dir

import de.mel.KResult
import java.io.File

class DirSettings(var dir: File? = null, var port: Int = 9333, var https: Boolean = false, var workingDir: File = File("certs"), var debug: Boolean = false) : KResult