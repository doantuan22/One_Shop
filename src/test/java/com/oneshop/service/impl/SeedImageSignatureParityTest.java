package com.oneshop.service.impl;

import com.oneshop.exception.BadRequestException;
import com.oneshop.seed.SeedImages;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The seed image pipeline must accept exactly what CloudinaryServiceImpl accepts (JPEG, PNG, WebP; never SVG). */
class SeedImageSignatureParityTest {

    private static byte[] padded(int... head) {
        byte[] b = new byte[64];
        for (int i = 0; i < head.length; i++) b[i] = (byte) head[i];
        return b;
    }

    private static boolean serviceAccepts(byte[] bytes) {
        try {
            CloudinaryServiceImpl.validateFileSignature(bytes);
            return true;
        } catch (BadRequestException ex) {
            return false;
        }
    }

    @Test
    void pipelineAndServiceAgreeOnSignatures() {
        byte[] webp = padded('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
        List<byte[]> samples = List.of(
                padded(0xFF, 0xD8, 0xFF, 0xE0),
                padded(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A),
                webp,
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8),
                padded('M', 'Z', 0x90, 0),
                padded('G', 'I', 'F', '8', '9', 'a'),
                new byte[3],
                padded('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'));
        for (byte[] s : samples) {
            assertThat(SeedImages.hasAllowedSignature(s)).as("sample of %d bytes starting 0x%02x", s.length, s.length > 0 ? s[0] : 0)
                    .isEqualTo(serviceAccepts(s));
        }
        assertThat(SeedImages.hasAllowedSignature(webp)).isTrue();
        assertThat(SeedImages.hasAllowedSignature("<svg></svg>          ".getBytes(StandardCharsets.UTF_8))).isFalse();
    }
}
