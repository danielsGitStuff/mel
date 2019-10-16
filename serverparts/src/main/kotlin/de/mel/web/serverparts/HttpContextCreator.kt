package de.mel.web.serverparts

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsServer
import de.mel.Lok
import de.mel.auth.tools.N

/**
 *     Creates contexts for an HttpsServer and provides methods for easy access to the provided parameters for GET and POST requests.
 *     Create an instance the usual way and call createContext(). The returned ContextInit object lets you specify handlers for the requests.
 *     GET request handlers are created by calling withGET(). You can add expected values by calling [expect] (key,expectedValue) on the RequestHandler which returns Expectations.
 *     You can daisy chain them by adding and(key,expectedValue). If a match occurs whatever you specify in expectation.handle() or requestHandler.handle() is executed.
 *
 *
 *     **Keep in mind that if you call [RequestHandler handle] this one will match all requests. That means that all subsequent RequestHandlers are ignored.**
 *     Only the first matching RequestHandler is invoked. **Requesthandlers are tried in the order you create them!**
 */
class HttpContextCreator(val server: HttpServer) {


    /**
     * checks if the requirements (request parameters) are met and executes code dealing with it.
     * call expect to check parameters. if all Expectations are met, the cuntion you hand over to
     * Expectation.handle() is executed. If no Expectations are present, the function handed over in RequestHandeler.handle()
     * will allways be exectuted when the server context is matched.
     */
    class RequestHandler(internal val contextInit: ContextInit) {
        var handleFunction: ((HttpExchange, QueryMap) -> Boolean)? = null
        var isGeneralHandler = true
        var closeWhenHandled = true

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
            isGeneralHandler = false
            val expectation = Expectation(this, key, expectFunction)
            return expectation
        }

        fun handle(handleFunction: (HttpExchange, QueryMap) -> Unit): ContextInit {
            // wrap and return true
            this.handleFunction = { httpExchange, queryMap ->
                handleFunction.invoke(httpExchange, queryMap)
                true
            }

            return contextInit
        }

        internal fun onContextCalled(httpExchange: HttpExchange, queryMap: QueryMap): Boolean {
            if (handleFunction == null)
                return false
            return handleFunction!!.invoke(httpExchange, queryMap)
        }

        fun dontCloseExchange(): RequestHandler {
            closeWhenHandled = false
            return this
        }
    }


    open class ContextInit {
        private var noMatchFunction: ((HttpExchange, QueryMap) -> Unit)? = null
        private var errorFunction: ((HttpExchange, Exception) -> Unit)? = null


        private val fallbackErrorFunction: ((HttpExchange, Exception) -> Unit) = { httpExchange, exception ->
            try {
                with(httpExchange) {
                    val text = "something went wrong"
                    de.mel.Lok.debug("sending error to $remoteAddress")
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
            /**
             * check if the request matches a handler. If no match occurs result is false.
             * if an exception is thrown, result is null.
             */
            var result: Boolean? = null
                Lok.debug("handling request(${httpExchange.requestMethod}) for ${httpExchange.requestURI}")
                try {
                    if (httpExchange.requestMethod == "POST") {
                        N.oneLine { queryMap.fillFomPost(httpExchange) }
                        result = runRequestHandlers(postHandlers, queryMap, httpExchange)
                    } else if (httpExchange.requestMethod == "GET") {
                        N.oneLine { queryMap.fillFromGet(httpExchange) }
                        result = runRequestHandlers(getHandlers, queryMap, httpExchange)
                    } else {
                        Lok.error("unknown request method; ${httpExchange.requestMethod}")
                        result = false
                    }
                } catch (e: Exception) {
                    // exception, result stays null
                    onExceptionThrown(httpExchange, e)
                }
                // no match occured
                if (result != null && !result) {
                    // no custum function available
                    if (noMatchFunction == null) {
                        try {
                            with(httpExchange) {
                                val text = "I just don't know what to do with myself! DADADA!"
                                de.mel.Lok.debug("sending error to $remoteAddress")
                                sendResponseHeaders(400, text.toByteArray().size.toLong())
                                responseBody.write(text.toByteArray())
                                responseBody.close()
                            }
                        } finally {
                            httpExchange.close()
                        }
                    } else {
                        // someone told me to do something, so I do it
                        noMatchFunction!!.invoke(httpExchange, queryMap)

                    }
                }
        }

        private fun runRequestHandlers(requestHandlers: List<RequestHandler>, queryMap: QueryMap, httpExchange: HttpExchange): Boolean {
            for (requestHandler in requestHandlers) {
                try {
                    if (requestHandler.onContextCalled(httpExchange, queryMap)) {
                        if (requestHandler.closeWhenHandled)
                            httpExchange.close()
                        return true
                    }
                }catch (e:Exception){
                    Lok.error("request handling failed for ")
                    requestHandler.closeWhenHandled
                    return true
                }
            }
            return false
        }


        internal fun onExceptionThrown(httpExchange: HttpExchange, e: Exception) {
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

        internal val postHandlers = mutableListOf<RequestHandler>()
        internal val getHandlers = mutableListOf<RequestHandler>()

        fun onError(onErrorFunction: (HttpExchange, Exception) -> Unit): ContextInit {
            this.errorFunction = onErrorFunction
            return this
        }

        private fun onErrorFunctionNullorFailed(httpExchange: HttpExchange, ee: Exception) {
            fallbackErrorFunction.invoke(httpExchange, ee)
        }

        fun onNoMatch(noMatchFunction: (HttpExchange, QueryMap) -> Unit): ContextInit {
            this.noMatchFunction = noMatchFunction
            return this
        }
    }

    fun createContext(path: String): ContextInit {
        val contextInit = ContextInit()
        server.createContext(path) { ex ->
            contextInit.contextCalled(ex)
        }
        return contextInit
    }
}