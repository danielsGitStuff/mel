package de.mein.serverparts

import org.bouncycastle.crypto.tls.ConnectionEnd.server
import org.junit.Before
import org.junit.Test

class ExpectationTest {
    var queryMap: QueryMap? = null
    @Before
    fun setUp() {
        val contextCreator = HttpContextCreator(server)
        contextCreator.createContext("").withGet().handle {  }
        queryMap = QueryMap().parseGet("?=a=a&b=b&c=c")
    }

    @Test
    fun toBe() {
        queryMap!!.expect("a","a").and("b","b").handle {  }
    }

    @Test
    fun isTrue() {

    }
}