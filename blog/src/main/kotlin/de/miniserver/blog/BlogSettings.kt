package de.miniserver.blog

import de.mein.KResult
import de.mein.auth.data.JsonSettings
import de.mein.auth.data.access.CertificateManager
import java.io.File

class BlogSettings : JsonSettings(), KResult {


    var name: String? = "Penis!"
    var motto = "Kein Mensch braucht noch eine neue Blogsoftware, aber hier ist sie! TADAAA!"
    var user: String? = "user"
    var password: String? = "no"
    var port: Int? = DEFAULT_PORT
    var blogDir: File? = null
    var subUrl: String? = "blog"
    override fun init() {

    }

    companion object {
        const val DEFAULT_KEY_SIZE = 4096
        const val DEFAULT_PORT = 4090

        fun loadBlogSettings(blogDir: File): BlogSettings {
            val settingsFile = File(blogDir, "blog.json")
            if (!settingsFile.exists()) {
                val settings = BlogSettings()
                settings.setJsonFile(settingsFile)
                settings.password = CertificateManager.randomUUID().toString()
                return settings
            }
            val settings = JsonSettings.load(settingsFile) as BlogSettings
            with(settings) {
                if (port == null)
                    port = DEFAULT_PORT
                if (subUrl == null)
                    subUrl = "/blog"
            }
            settings.blogDir = blogDir
            return settings
        }
    }
}