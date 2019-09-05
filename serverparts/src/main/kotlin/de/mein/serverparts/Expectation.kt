package de.mein.serverparts


class Expectation(val contextInit: HttpContextCreator.ContextInit, val queryMap: QueryMap, val key: String, val expectedValue: String?) {

    internal constructor(contextInit: HttpContextCreator.ContextInit, queryMap: QueryMap, key: String, expectedValue: String?, parent: Expectation) : this(contextInit, queryMap, key, expectedValue) {
        this.parent = parent
    }

    private var parent: Expectation? = null
    var next: Expectation? = null
//    var next: CompleteExpectation? = null

    fun and(key: String, expectedValue: String?): Expectation {
        next = Expectation(contextInit, queryMap, key, expectedValue, this)
        return next!!
    }


    internal fun isFulfilled(): Boolean {
        val value = queryMap[key]
        val thisExpectation = value == expectedValue
        if (!thisExpectation)
            return false
        if (parent != null)
            return parent!!.isFulfilled()
        return true
    }

    fun handle(thenDo: () -> Any): HttpContextCreator.ContextInit {
        if (isFulfilled()) {
            thenDo.invoke()
        }
        return contextInit
    }

}

