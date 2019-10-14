package de.mel.auth.socket

import de.mel.Lok
import de.mel.auth.data.MelRequest
import de.mel.auth.data.ResponseException
import de.mel.sql.RWLock
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * this is a replacement for WatchDogTimers in a MelRequest. (when finally everything is ported to kotlin)
 */
class RequestTimer(val request: MelRequest) {
    private var job: Job? = null
    private val repetitions = 30
    var done = AtomicBoolean(false)

    suspend fun doTimerThings(): Unit = withContext(Dispatchers.Default) {
        job?.cancel()
        job = launch(Dispatchers.Default) {

            repeat(repetitions) {
                Lok.debug("waiting $it")
                delay(1000)
            }
            if (!done.get())
                request.answerDeferred.reject(ResponseException("too long"))
            Lok.debug("done ")
        }
    }

    fun success() {
        Lok.debug("success")
        done.set(true)
        job?.cancel()
        job = null
    }

    fun fail(msg: String): Unit {
        fail(ResponseException(msg))
    }

    fun fail(e: ResponseException): Unit {
        Lok.debug("fail")
        done.set(true)
        job?.cancel()
        request.answerDeferred.reject(e)
        job = null
    }

    suspend fun restart(): Unit {
        doTimerThings()
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            launch {
                val request = MelRequest("test", "intent")
                request.answerDeferred.done {
                    Lok.debug("done")
                }.fail {
                    Lok.debug("failed successfully")
                }
                val timer = RequestTimer(request)
                launch(Dispatchers.Default) {
                    timer.doTimerThings()
                }
                Lok.debug("wait main")
                delay(2500L)
                timer.restart()
                RWLock().lockWrite().lockWrite()
            }
        }
    }
}