package com.oneshop.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The address of the real client. {@code X-Forwarded-For} is only believed when the TCP peer is a proxy of ours
 * (loopback / private network: nginx on the same host or a load balancer); the entry appended by that proxy is the
 * last one, anything to its left was supplied by the client and is ignored.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (peer == null) {
            return "unknown";
        }
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        String last = forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
        return last.isEmpty() || last.length() > 45 ? peer : last;
    }

    static boolean isTrustedProxy(String address) {
        try {
            if (!address.matches("[0-9a-fA-F:.]+")) {
                return false; // never resolve a host name
            }
            InetAddress ip = InetAddress.getByName(address);
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
