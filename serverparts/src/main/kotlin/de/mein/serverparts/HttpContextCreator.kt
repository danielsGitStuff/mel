package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsServer

open class HttpContextCreator(val server: HttpsServer) {

    abstract class AbstractRequestHandler(val contextInit: ContextInit, val queryMap: QueryMap) {

        fun expect(key: String, expectedValue: String?): Expectation {
            return queryMap.expect(key, expectedValue)
        }

        fun handle(function: () -> Unit) {
            function.invoke()
        }
    }

    class GetRequestHandler(contextInit: ContextInit, queryMap: QueryMap) : AbstractRequestHandler(contextInit, queryMap) {

    }

    class Comp(key: String, value: String?) {}

    open class ContextInit {
        var queryMapGet: QueryMap? = null
        lateinit var httpExchange: HttpExchange

        fun contextCalled(exchange: HttpExchange): Unit {
            this.httpExchange = exchange
        }

        fun withGet(): GetRequestHandler {
            if (queryMapGet == null) {
                queryMapGet = QueryMap(this)
                queryMapGet!!.fillFromGet(httpExchange)
            }
            return GetRequestHandler(this, queryMapGet!!)
        }
    }

    fun createContext(path: String): ContextInit {
        val contextContainer = ContextInit()
        server.createContext(path) { ex ->
            contextContainer.contextCalled(ex)
        }
        return contextContainer
    }
}