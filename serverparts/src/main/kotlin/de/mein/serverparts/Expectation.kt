package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange

class Expectation(val requestHandler: HttpContextCreator.RequestHandler, val key: String, val expectFunction: (String?) -> Boolean) {

    internal constructor(requestHandler: HttpContextCreator.RequestHandler, key: String, expectFunction: (String?) -> Boolean, parent: Expectation) : this(requestHandler, key, expectFunction) {
        this.parent = parent
    }

    private var parent: Expectation? = null
    var next: Expectation? = null

    fun and(key: String, expectedValue: String?): Expectation {
        return and(key) { it == expectedValue }
    }

    fun and(key: String, expectFunction: (String?) -> Boolean): Expectation {
        next = Expectation(requestHandler, key, expectFunction, this)
        // set the handle function on the RequestHandler, so it has something to execute even if no method is provided in Expectation.handle()
        requestHandler.handleFunction = { httpExchange, queryMap -> isFulfilled(queryMap) }
        return next!!
    }


    internal fun isFulfilled(queryMap: QueryMap): Boolean {
        val value = queryMap[key]
        val thisExpectation = expectFunction.invoke(value)
        if (!thisExpectation)
            return false
        if (parent != null)
            return parent!!.isFulfilled(queryMap)
        return true
    }

    fun handle(handleFunction: (HttpExchange, QueryMap) -> Unit): HttpContextCreator.ContextInit {
        // wrap the handle function so our expectations are checked before executing it
        requestHandler.handleFunction = { httpExchange, queryMap ->
            if (isFulfilled(queryMap)) {
                try {
                    handleFunction.invoke(httpExchange, queryMap)
                } catch (e: Exception) {
                    requestHandler.contextInit.onExceptionThrown(httpExchange, e)
                }
                true
            } else
                false
        }
        return requestHandler.contextInit
    }

}