package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import java.lang.Exception


class Expectation(val contextInit: HttpContextCreator.ContextInit, val queryMap: QueryMap, val key: String, val expectFunction: (String?) -> Boolean) {

    internal constructor(contextInit: HttpContextCreator.ContextInit, queryMap: QueryMap, key: String, expectFunction: (String?) -> Boolean, parent: Expectation) : this(contextInit, queryMap, key, expectFunction) {
        this.parent = parent
    }

    private var parent: Expectation? = null
    var next: Expectation? = null

    fun and(key: String, expectedValue: String?): Expectation {
        return and(key) { it == expectedValue }
    }

    fun and(key: String, expectFunction: (String?) -> Boolean): Expectation {
        next = Expectation(contextInit, queryMap, key, expectFunction, this)
        return next!!
    }


    internal fun isFulfilled(): Boolean {
        val value = queryMap[key]
        val thisExpectation = expectFunction.invoke(value)
        if (!thisExpectation)
            return false
        if (parent != null)
            return parent!!.isFulfilled()
        return true
    }

    fun handle(thenDo: (HttpExchange, QueryMap) -> Unit): HttpContextCreator.ContextInit {
        if (isFulfilled()) {
            try {
                thenDo.invoke(contextInit.httpExchange, queryMap)
            } catch (e: Exception) {
                contextInit.onExceptionThrown(e)
            }
        }
        return contextInit
    }

}

