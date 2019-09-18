package de.mein.auth.service

import de.mein.auth.data.ServicePayload
import de.mein.auth.socket.ConnectWorker
import de.mein.auth.socket.MeinValidationProcess


class FailedConnectResult(val exeption: Exception) : ConnectResult(null) {
    override fun onAuthenticated(function: (MeinValidationProcess) -> Unit): ConnectResult = this

    override fun request(serviceUuid: String, payload: ServicePayload): RequestResult {
        return RequestResult().fail(exeption)
    }

    override fun onFail(function: (Exception) -> Unit): ConnectResult {
        return super.onFail(function)
    }
}

open class ConnectResult(val connectWorker: ConnectWorker?) {
    private lateinit var meinValidationProcess: MeinValidationProcess
    private lateinit var exception: Exception


    open fun onAuthenticated(function: (MeinValidationProcess) -> Unit): ConnectResult {
        try {
            connectWorker?.authenticateJob?.done { function(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            return this
        }
    }

    open fun request(serviceUuid: String, payload: ServicePayload): RequestResult {
        val result = RequestResult()
        onAuthenticated {
            it.request(serviceUuid, payload).done {
                result.sucess(it)
            }.fail {
                result.fail(it)
            }
        }
        onFail {
            result.fail(it)
        }
        return result
    }

    open fun onFail(function: (Exception) -> Unit): ConnectResult {
        try {
            connectWorker?.authenticateJob?.fail { function(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            return this
        }
    }
}


class RequestResult() {
    private var sucessFunction: ((Any) -> Unit)? = null
    private var failFunction: ((Exception) -> Unit)? = null

    internal fun sucess(result: Any): RequestResult {
        try {
            sucessFunction?.invoke(result)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    internal fun fail(e: Exception): RequestResult {
        try {
            failFunction?.invoke(e)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    fun onSuccess(function: (Any) -> Unit): RequestResult {
        this.sucessFunction = function
        return this
    }

    fun onFail(function: (Exception) -> Unit): RequestResult {
        this.failFunction = function
        return this
    }

}