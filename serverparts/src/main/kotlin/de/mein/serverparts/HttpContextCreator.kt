package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import java.lang.Exception

open class HttpContextCreator(val server: HttpsServer) {

    class RequestHandler(private val contextInit: ContextInit, private val queryMap: QueryMap) {
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
            return queryMap.expect(key, expectFunction)
        }

        fun handle(function: (HttpExchange) -> Unit): ContextInit {
            try {
                function.invoke(contextInit.httpExchange)
            } catch (e: Exception) {
                contextInit.onExceptionThrown(e)
            }
            return contextInit
        }
    }


    open class ContextInit {
        private var errorFunction: ((HttpExchange, Exception) -> Unit)? = null
        private var queryMapPost: QueryMap? = null
        private var queryMapGet: QueryMap? = null
        lateinit var httpExchange: HttpExchange

        private val fallbackErrorFunction: ((Exception) -> Unit) = { exception ->
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


        internal fun contextCalled(exchange: HttpExchange) {
            this.httpExchange = exchange
        }

        fun onExceptionThrown(e: Exception) {
            Lok.debug("error happened")
            e.printStackTrace()
            if (errorFunction != null) {
                try {
                    errorFunction!!.invoke(httpExchange, e)
                } catch (ee: Exception) {
                    onErrorFunctionNullorFailed(ee)
                }
            }
        }

        fun withPOST(): RequestHandler {
            if (queryMapPost == null) {
                queryMapPost = QueryMap(this)
            }
            val handler = RequestHandler(this, queryMapPost!!)
            postHandlers.add(handler)
            return handler
        }

        fun withGET(): RequestHandler {
            if (queryMapGet == null) {
                queryMapGet = QueryMap(this)
            }
            val handler = RequestHandler(this, queryMapPost!!)
            postHandlers.add(handler)
            return handler
        }

        val postHandlers = mutableListOf<RequestHandler>()
        val getHandlers = mutableListOf<RequestHandler>()

        fun onError(onErrorFunction: (HttpExchange, Exception) -> Unit): ContextInit {
            this.errorFunction = onErrorFunction
            return this
        }

        private fun onErrorFunctionNullorFailed(ee: Exception) {
            fallbackErrorFunction.invoke(ee)
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