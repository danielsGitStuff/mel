package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsServer
import de.mein.Lok

open class HttpContextCreator(val server: HttpsServer) {



    class RequestHandler(internal val contextInit: ContextInit) {
        var handleFunction: ((HttpExchange, QueryMap) -> Unit)? = null

        /**
         * checks whether parameters of the request matches a criterion
         * @param key key in the parameter map
         * @param expectedValue the value you expect
         */
        fun expect(key: String, expectedValue: String?): Expectation {
            return expect(key) { it == expectedValue }
        }

        /**
         * checks whether parameters of the request matches a criterion
         * @param key key in the parameter map
         * @param expectFunction if you expect more sophisticated things
         */
        fun expect(key: String, expectFunction: (String?) -> Boolean): Expectation {
            val expectation = Expectation(this, key, expectFunction)
            return expectation
        }

        fun handle(handleFunction: (HttpExchange, QueryMap) -> Unit): ContextInit {
            this.handleFunction = handleFunction

            return contextInit
        }

        internal fun onContextCalled(httpExchange: HttpExchange, queryMap: QueryMap) {
            try {
                handleFunction?.invoke(httpExchange, queryMap)
            } catch (e: Exception) {
                contextInit.onExceptionThrown(httpExchange, e)
            }
        }
    }


    open class ContextInit {
        private var errorFunction: ((HttpExchange, Exception) -> Unit)? = null


        private val fallbackErrorFunction: ((HttpExchange, Exception) -> Unit) = { httpExchange, exception ->
            try {
                with(httpExchange) {
                    val text = "something went wrong"
                    de.mein.Lok.debug("sending error to $remoteAddress")
                    sendResponseHeaders(400, text.toByteArray().size.toLong())
                    responseBody.write(text.toByteArray())
                    responseBody.close()
                }
            } finally {
                httpExchange.close()
            }
        }


        internal fun contextCalled(httpExchange: HttpExchange) {
            val queryMap = QueryMap()
            if (httpExchange.requestMethod == "POST") {
                queryMap.fillFomPost(httpExchange)
                postHandlers.forEach { it.onContextCalled(httpExchange, queryMap) }
            } else if (httpExchange.requestMethod == "GET") {
                queryMap.fillFromGet(httpExchange)
                getHandlers.forEach { it.onContextCalled(httpExchange, queryMap) }
            } else
                Lok.error("unknown request method; ${httpExchange.requestMethod}")
        }


        fun onExceptionThrown(httpExchange: HttpExchange, e: Exception) {
            Lok.debug("error happened")
            e.printStackTrace()
            if (errorFunction != null) {
                try {
                    errorFunction!!.invoke(httpExchange, e)
                } catch (ee: Exception) {
                    onErrorFunctionNullorFailed(httpExchange, ee)
                }
            }
        }

        fun withPOST(): RequestHandler {
            val handler = RequestHandler(this)
            postHandlers.add(handler)
            return handler
        }

        fun withGET(): RequestHandler {
            val handler = RequestHandler(this)
            getHandlers.add(handler)
            return handler
        }

        val postHandlers = mutableListOf<RequestHandler>()
        val getHandlers = mutableListOf<RequestHandler>()

        fun onError(onErrorFunction: (HttpExchange, Exception) -> Unit): ContextInit {
            this.errorFunction = onErrorFunction
            return this
        }

        private fun onErrorFunctionNullorFailed(httpExchange: HttpExchange, ee: Exception) {
            fallbackErrorFunction.invoke(httpExchange, ee)
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