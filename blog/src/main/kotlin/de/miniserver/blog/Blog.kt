package de.miniserver.blog

import de.mein.konsole.Konsole

class Blog(val settings: BlogSettings) {

    companion object {
        @JvmStatic
        fun main(arguments: Array<String>) {

            val path = System.getProperty("user.dir")
            println("Working Directory = $path")

            val konsole = Konsole(BlogSettings())
//            konsole.optional("-cert","path to cert",{ result, args -> result.certPath })

            val settings = konsole.handle(arguments).result as BlogSettings
            var blog: Blog? = null
            try {
                blog = Blog(settings)
                blog.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun start() {

    }
}