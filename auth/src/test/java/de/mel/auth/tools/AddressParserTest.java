package de.mel.auth.tools;

import de.mel.auth.data.db.CertificateAddress;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AddressParserTest {
    @Test
    public void parsesValidAddresses() {
        CertificateAddress hostOnly = AddressParser.parse("  my.host.example  ");
        assertEquals("my.host.example", hostOnly.getAddress().v());
        assertNull(hostOnly.getPort().v());

        CertificateAddress hostPort = AddressParser.parse("192.168.1.10:8888");
        assertEquals("192.168.1.10", hostPort.getAddress().v());
        assertEquals(8888, hostPort.getPort().v());

        CertificateAddress ipv6 = AddressParser.parse("[2001:db8::1]");
        assertEquals("2001:db8::1", ipv6.getAddress().v());
        assertNull(ipv6.getPort().v());

        CertificateAddress ipv6Port = AddressParser.parse("[::1]:9000", 9001);
        assertEquals("::1", ipv6Port.getAddress().v());
        assertEquals(9000, ipv6Port.getPort().v());
        assertEquals(9001, ipv6Port.getCertPort().v());

        assertEquals("my.host.example", AddressParser.format(hostOnly));
        assertEquals("192.168.1.10:8888", AddressParser.format(hostPort));
        assertEquals("[2001:db8::1]", AddressParser.format(ipv6));
        assertEquals("[::1]:9000", AddressParser.format(ipv6Port));
    }

    @Test
    public void rejectsInvalidAddresses() {
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("http://example.com"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("example.com/path"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("example.com:0"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("example.com:65536"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("example.com:abc"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("[::1"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> AddressParser.parse("bad host"));
    }

    @Test
    public void deduplicatesByAddressAndPort() {
        List<CertificateAddress> deduped = AddressParser.deduplicate(Arrays.asList(
                AddressParser.parse("host1:8888"),
                AddressParser.parse("host1:8888"),
                AddressParser.parse("host1:8889"),
                AddressParser.parse("host1")
        ));
        assertEquals(3, deduped.size());
    }
}
