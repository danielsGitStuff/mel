package de.mel.web.blog

import com.sun.net.httpserver.HttpExchange
import de.mel.Lok
import de.mel.web.serverparts.Sessions

/**
 * checks user and password once and remembers the login in a session cookie that is only sent for the blog's URLs
 */
class BlogAuthenticator(private val blogSettings: BlogSettings, secure: Boolean) {
    private val sessions = Sessions("blogsession", "/${blogSettings.subUrl}", secure)

    private fun checkCredentials(user: String?, pw: String?): Boolean {
        val result = user == blogSettings.user && Sessions.passwordMatches(pw, blogSettings.password)
        if (result)
            Lok.debug("auth=true")
        else
            Lok.debug("auth=false")
        return result
    }

    /**
     * starts a session if [user] and [pw] are right. call before the response headers are sent.
     */
    fun login(exchange: HttpExchange, user: String?, pw: String?): Boolean {
        if (!checkCredentials(user, pw))
            return false
        sessions.start(exchange)
        return true
    }

    fun isLoggedIn(exchange: HttpExchange): Boolean = sessions.isValid(exchange)
}
