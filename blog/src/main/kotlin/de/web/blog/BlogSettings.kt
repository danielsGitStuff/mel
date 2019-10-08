package de.web.blog

import de.mel.KResult
import de.mel.auth.data.JsonSettings
import de.mel.auth.data.access.CertificateManager
import java.io.File

class BlogSettings : JsonSettings(), KResult {

    var name: String? = "Penis!"
    var motto = "Kein Mensch braucht noch eine neue Blogsoftware, aber hier ist sie! TADAAA!"
    var user: String? = "user"
    var password: String? = "no"
    var port: Int? = DEFAULT_PORT
    var blogDir: File? = null
    var subUrl: String? = "blog"
    var countVisitors: Boolean? = false
    override fun init() {

    }

    companion object {
        const val DEFAULT_KEY_SIZE = 4096
        const val DEFAULT_PORT = 4090

        fun loadBlogSettings(blogDir: File): BlogSettings {
            val settingsFile = File(blogDir, "blog.json")
            var settings: BlogSettings? = null
            if (!settingsFile.exists()) {
                val createdSettings = BlogSettings()
                createdSettings.setJsonFile(settingsFile)
                createdSettings.password = CertificateManager.randomUUID().toString()
                settings = createdSettings
            } else {
                settings = JsonSettings.load(settingsFile) as BlogSettings
                with(settings) {
                    if (port == null)
                        port = DEFAULT_PORT
                    if (subUrl == null)
                        subUrl = "blog"
                    if (countVisitors == null)
                        countVisitors = false
                }
            }
            settings.blogDir = blogDir
            return settings
        }
    }
}