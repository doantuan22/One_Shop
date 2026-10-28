package com.oneshop.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void aDirectClientCannotSpoofItsAddressWithAHeader() {
        assertThat(ClientIp.of(request("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    void behindOurProxyTheEntryTheProxyAppendedIsUsed() {
        // client sent "6.6.6.6"; nginx appended the address it really saw
        assertThat(ClientIp.of(request("127.0.0.1", "6.6.6.6, 203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(ClientIp.of(request("10.0.1.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void withoutAForwardedHeaderThePeerIsUsed() {
        assertThat(ClientIp.of(request("127.0.0.1", null))).isEqualTo("127.0.0.1");
        assertThat(ClientIp.of(request("127.0.0.1", "  "))).isEqualTo("127.0.0.1");
    }

    @Test
    void hostNamesAreNeverTrustedOrResolved() {
        assertThat(ClientIp.isTrustedProxy("localhost")).isFalse();
        assertThat(ClientIp.isTrustedProxy("example.com")).isFalse();
    }
}
