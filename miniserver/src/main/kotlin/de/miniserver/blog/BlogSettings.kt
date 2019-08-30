package de.miniserver.blog

import de.mein.auth.data.JsonSettings
import de.mein.auth.data.access.CertificateManager
import java.io.File

class BlogSettings : JsonSettings() {

    var name: String? = "Penis!"
    var motto = "Kein Mensch braucht noch eine neue Blogsoftware, aber hier ist sie! TADAAA!"
    var user : String? = "user"
    var password : String? = "no"
    override fun init() {

    }

    companion object {
        fun loadBlogSettings(blogDir: File): BlogSettings {
            val settingsFile = File(blogDir, "blog.json")
            if (!settingsFile.exists()) {
                val settings = BlogSettings()
                settings.setJsonFile(settingsFile)
                settings.password = CertificateManager.randomUUID().toString()
                return settings
            }
            val settings = JsonSettings.load(settingsFile) as BlogSettings
            return settings
        }
    }
}