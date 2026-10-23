# Mel.serverparts
These classes are shared between miniserver and blog.


## What is in here
- `AbstractHttpsThingy`: an HTTP(S) server on the JDK's `com.sun.net.httpserver`. `respond(...)` sends and always closes the exchange; `respondText`, `respondBinary`, `respondPage`, `respondNotFound`, `respondError` (fills the template `errorPagePath` if a subclass sets one, else plain text) and `redirect` build on it.
- `HttpContextCreator`: routing (`createContext(path).withGET().expect(key){...}.and(...).handle {...}`, first match wins). Every request gets an answer: a handler that throws gets the `onError` function or a plain 500, no match gets `onNoMatch` or a 404.
- `QueryMap`: GET queries and form POST bodies, URL-decoded (`QueryMap.parse`).
- `Page` + `Replacer`: templates with `<§=name/>` tags. A replacement is inserted as markup unless the `Replacer` says `escapeHtml()`; use that (or `Html.escape` when building HTML in code) for everything that is not your own markup.
- `Sessions`: login once, then a random token in a cookie (`HttpOnly`, `SameSite=Strict`, `Secure` on HTTPS), kept in memory (a restart logs out). `Sessions.passwordMatches` compares in constant time. No password goes back into a page.
- `MimeTypeReader`: content type by extension, `Files.probeContentType` as fallback.

Tests: `ServerPartsTest` (port 35100), the blog has `BlogThingyTest` (port 35200).

## TLS
The HTTPS server offers TLS 1.3 and 1.2 (1.3 suites plus the 1.2 suites of `CertificateManager.filterCipherSuites`).
Mel's own sockets (`CertificateManager`) are still pinned to TLS 1.2, see below.

## High CPU-load? (history)
There is a bug in openJDK 11 (still present in openJDK 12) that causes an infinite loop when dealing with tls 1.3.
The high CPU load seems to be related to TSL v1.3. It occurs when the server is running for several hours, sometimes minutes. I was not able to reproduce it reliably.

~~To workaround start the JVM with `-Djdk.tls.acknowledgeCloseNotify=true`. << still not evaluated~~

~~See [here](https://bugs.openjdk.java.net/browse/JDK-8208526) and [here](https://stackoverflow.com/questions/56708300/httpsserver-causes-100-cpu-load-with-curl).~~

~~And [another bug causing an infinite loop that is related to TSL v1.3](https://stackoverflow.com/questions/54485755/java-11-httpclient-leads-to-endless-ssl-loop) 
It proposes starting the JVM with `-Djdk.tls.disabledAlgorithms=TLSv1.3` << does not work~~

Until this was fixed in the JDK the only available option was TLSv1.2. It is fixed on JDK 21, so `AbstractHttpsThingy` allows 1.3 again (2026-10-23).
The pin in `CertificateManager` (Mel's phone/PC sockets) is still there: lifting it belongs with a device test.