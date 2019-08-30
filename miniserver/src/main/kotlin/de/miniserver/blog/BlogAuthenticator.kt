package de.miniserver.blog

import com.sun.net.httpserver.BasicAuthenticator
import com.sun.net.httpserver.HttpExchange
import de.mein.Lok
import de.mein.auth.tools.N

class BlogAuthenticator(val blogThingy: BlogThingy) {
    companion object {
        val HEADER_AUTH = "Authorization"
    }

    private fun checkCredentials(user: String?, pw: String?): Boolean {
        val settings = blogThingy.blogSettings
        val result = settings.user == user && settings.password == pw
        if (result)
            Lok.debug("auth=true")
        else
            Lok.debug("auth=false")
        return result
    }

    private fun sendFail(exchange: HttpExchange) {
        with(exchange) {
            val response = "auth failed".toByteArray()
            sendResponseHeaders(403, response.size.toLong())
            responseBody.write(response)
            responseBody.close()
        }
    }

    fun check(exchange: HttpExchange, user: String?, pw: String?, onSuccess: N.INoTryRunnable, onFail: N.INoTryRunnable?) {
        var authenticated = checkCredentials(user, pw)
        if (authenticated) {
            N.r(onSuccess)
        } else if (onFail != null) {
            N.r(onFail)
        } else {
            sendFail(exchange)
        }
    }

    fun check(exchange: HttpExchange, onSuccess: N.INoTryRunnable, onFail: N.INoTryRunnable?) {
        val authInfo = exchange.requestHeaders.get(HEADER_AUTH)
        var authenticated = false
        if (authInfo != null) {
            val user = authInfo[0]
            val pw = "p"
            authenticated = checkCredentials(user, pw)
        }
        if (authenticated) {
            N.r(onSuccess)
        } else if (onFail != null) {
            N.r(onFail)
        } else {
            sendFail(exchange)
        }
    }

    fun authenticate(exchange: HttpExchange, user: String?, pw: String?, onSuccess: N.INoTryRunnable, onFail: N.INoTryRunnable?) {
        if (checkCredentials(user, pw)) {
            val headers = exchange.responseHeaders
//            val authList = mutableListOf("u=$user", "p=$pw")
//            headers.set(HEADER_AUTH, authList)
            headers.set(HEADER_AUTH, "Basic kekse")
            N.r(onSuccess)
        } else if (onFail != null) {
            N.r(onFail)
        } else {
            sendFail(exchange)
        }
    }

//    fun checkAuth(exchange: HttpExchange, user: String?, pw: String?, onSuccess: N.INoTryRunnable, onFail: N.INoTryRunnable?) {
//        var authenticated = checkCredentials(user, pw)
//        if (!authenticated)
//            authenticated = authenticate(exchange)::class.java == Success::class.java
//
//        val test = authenticate(exchange)::class.java == Success::class.java
////        var authenticated = this.authenticate(exchange)::class.java == Success::class.java
////
////        if (!authenticated)
////            authenticated = checkCredentials(user, pw)
//
//        if (authenticated) {
//            N.r(onSuccess)
//        } else if (onFail != null) {
//            N.r(onFail)
//        } else {
//            with(exchange) {
//                val response = "auth failed".toByteArray()
//                sendResponseHeaders(403, response.size.toLong())
//                responseBody.write(response)
//                responseBody.close()
//            }
//        }
//    }
}