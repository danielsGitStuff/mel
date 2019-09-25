# Mel.serverparts
These classes are shared between miniserver and blog.


## High CPU-load?
There is a bug in openJDK 11 that causes an infinite loop when dealing with tls 1.3.
So workaround start the JVM with `-Djdk.tls.acknowledgeCloseNotify=true`.

See [here](https://bugs.openjdk.java.net/browse/JDK-8208526) ande [here](https://stackoverflow.com/questions/56708300/httpsserver-causes-100-cpu-load-with-curl).