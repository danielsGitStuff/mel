package de.mel.web.serverparts

import com.sun.net.httpserver.HttpExchange
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers who logged in, so the password is sent once and never ends up in a page.
 * [start] hands the browser a random token as cookie, [isValid] checks it on later requests.
 * SameSite=Strict keeps other sites from sending requests with it.
 * Tokens live in memory only: a restart logs everybody out.
 * @param path the cookie is only sent for URLs below it
 * @param secure send the cookie via HTTPS only. switch it off for plain HTTP or the browser never sends it back.
 */
class Sessions(private val cookieName: String,
               private val path: String = "/",
               private val secure: Boolean = true,
               private val lifetime: Duration = Duration.ofHours(12)) {
    private val random = SecureRandom()
    private val expiries = ConcurrentHashMap<String, Instant>()

    companion object {
        /**
         * compares both in constant time, so the time an answer takes does not tell how much of a guess was right
         */
        fun passwordMatches(given: String?, expected: String?): Boolean {
            if (given == null || expected == null)
                return false
            // hashing first: same length, isEqual does not stop early
            val sha = { s: String -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()) }
            return MessageDigest.isEqual(sha(given), sha(expected))
        }
    }

    /**
     * call before the response headers are sent
     */
    fun start(ex: HttpExchange) {
        val now = Instant.now()
        expiries.entries.removeIf { it.value.isBefore(now) }
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        expiries[token] = now.plus(lifetime)
        ex.responseHeaders.add("Set-Cookie", "$cookieName=$token; Path=$path; Max-Age=${lifetime.seconds}; HttpOnly; SameSite=Strict"
                + if (secure) "; Secure" else "")
    }

    fun isValid(ex: HttpExchange): Boolean {
        val token = tokenOf(ex) ?: return false
        val expiry = expiries[token] ?: return false
        if (Instant.now().isAfter(expiry)) {
            expiries.remove(token)
            return false
        }
        return true
    }

    private fun tokenOf(ex: HttpExchange): String? = ex.requestHeaders["Cookie"].orEmpty()
            .flatMap { it.split(";") }
            .map { it.trim() }
            .firstOrNull { it.startsWith("$cookieName=") }
            ?.substringAfter('=')
}
