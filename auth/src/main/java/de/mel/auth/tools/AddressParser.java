package de.mel.auth.tools;

import de.mel.auth.data.db.CertificateAddress;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses, validates, formats, and deduplicates alternative certificate addresses.
 */
public final class AddressParser {
    private AddressParser() {
    }

    public static CertificateAddress parse(String input) {
        return parse(input, null);
    }

    public static CertificateAddress parse(String input, Integer certPort) {
        if (input == null)
            throw new IllegalArgumentException("Address must not be empty");
        String trimmed = input.trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException("Address must not be empty");
        if (trimmed.contains("://"))
            throw new IllegalArgumentException("URL schemes are not allowed");
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("?") || trimmed.contains("#"))
            throw new IllegalArgumentException("Paths are not allowed");
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i)))
                throw new IllegalArgumentException("Address must not contain whitespace");
        }

        String host;
        Integer port = null;

        if (trimmed.startsWith("[")) {
            int closeIdx = trimmed.indexOf(']');
            if (closeIdx <= 1)
                throw new IllegalArgumentException("Invalid IPv6 address");
            host = trimmed.substring(1, closeIdx);
            if (host.contains("[") || host.contains("]"))
                throw new IllegalArgumentException("Invalid IPv6 address");
            String rest = trimmed.substring(closeIdx + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":"))
                    throw new IllegalArgumentException("Expected ':' after ']'");
                port = parsePort(rest.substring(1));
            }
        } else {
            if (trimmed.contains("[") || trimmed.contains("]"))
                throw new IllegalArgumentException("Unexpected brackets in address");
            int firstColon = trimmed.indexOf(':');
            if (firstColon < 0) {
                host = trimmed;
            } else {
                int lastColon = trimmed.lastIndexOf(':');
                if (firstColon == lastColon) {
                    host = trimmed.substring(0, firstColon);
                    if (host.isEmpty())
                        throw new IllegalArgumentException("Host must not be empty");
                    port = parsePort(trimmed.substring(firstColon + 1));
                } else {
                    // Unbracketed IPv6 literal without port
                    host = trimmed;
                }
            }
        }

        if (certPort != null && (certPort < 1 || certPort > 65535))
            throw new IllegalArgumentException("Cert port must be between 1 and 65535");

        return new CertificateAddress()
                .setAddress(host)
                .setPort(port)
                .setCertPort(certPort);
    }

    public static int parsePort(String portStr) {
        if (portStr == null || portStr.isEmpty())
            throw new IllegalArgumentException("Port must not be empty");
        try {
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + portStr);
        }
    }

    public static String format(CertificateAddress addr) {
        if (addr == null || addr.getAddress().v() == null)
            return "";
        String host = addr.getAddress().v();
        Integer port = addr.getPort().v();
        boolean isIpv6 = host.contains(":");
        if (port != null) {
            return (isIpv6 ? "[" + host + "]" : host) + ":" + port;
        }
        return isIpv6 ? "[" + host + "]" : host;
    }

    public static List<CertificateAddress> deduplicate(List<CertificateAddress> addresses) {
        List<CertificateAddress> result = new ArrayList<>();
        if (addresses == null)
            return result;
        Set<String> seen = new HashSet<>();
        for (CertificateAddress addr : addresses) {
            if (addr == null || addr.getAddress().v() == null)
                continue;
            String key = addr.getAddress().v() + "|" + addr.getPort().v();
            if (seen.add(key))
                result.add(addr);
        }
        return result;
    }
}
