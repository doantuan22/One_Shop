package com.oneshop.service;

import com.oneshop.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/** Order-scoped code, eight uppercase characters excluding ambiguous I/O/0/1. Never log codes. */
@Component
public class PickupCodeService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    public void verify(String supplied, String stored) {
        String normalized = supplied == null ? "" : supplied.strip().toUpperCase(Locale.ROOT);
        // Existing seed READY orders have six-character codes. Match that format only when this locked Order has it.
        boolean legacy = stored != null && stored.matches("[A-Z0-9]{6}");
        if (!normalized.matches(legacy ? "[A-Z0-9]{6}" : "[A-HJ-NP-Z2-9]{8}") || stored == null
                || !MessageDigest.isEqual(normalized.getBytes(StandardCharsets.US_ASCII), stored.getBytes(StandardCharsets.US_ASCII))) {
            throw new BadRequestException("Mã nhận hàng không hợp lệ.");
        }
    }
}
