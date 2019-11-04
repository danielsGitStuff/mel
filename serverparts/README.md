# Mel.serverparts
These classes are shared between miniserver and blog.


## High CPU-load?
There is a bug in openJDK 11 (still present in openJDK 12) that causes an infinite loop when dealing with tls 1.3.
The high CPU load seems to be related to TSL v1.3. It occurs when the server is running for several hours, sometimes minutes. I was not able to reproduce it reliably.

~~To workaround start the JVM with `-Djdk.tls.acknowledgeCloseNotify=true`. << still not evaluated~~

~~See [here](https://bugs.openjdk.java.net/browse/JDK-8208526) and [here](https://stackoverflow.com/questions/56708300/httpsserver-causes-100-cpu-load-with-curl).~~

~~And [another bug causing an infinite loop that is related to TSL v1.3](https://stackoverflow.com/questions/54485755/java-11-httpclient-leads-to-endless-ssl-loop) 
It proposes starting the JVM with `-Djdk.tls.disabledAlgorithms=TLSv1.3` << does not work~~

Until this is properly fixed in the JDK the only available option is set to TLSv1.2.

<small>To reenable the behaviour: remove restrictions to TLSv1.2 in CertificateManager and AbstractHttpsThingy</small>