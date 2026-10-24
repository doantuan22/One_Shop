package com.oneshop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Attribution of the product pictures (CC BY-SA 3.0, Open Beauty Facts). The manifest is a bundled resource
 * ({@code data/image-credits.json}, generated with the seed and mirrored in docs/seed/IMAGE_CREDITS.md); it holds no
 * personal data beyond the public contributor user names that the licence asks us to credit.
 */
@Service
public class ImageCreditsService {

    public record Item(String sku, String name, String author, String obfCode, String obfPage) {
    }

    public record Credits(String verifiedOn, String license, String licenseUrl, String source, String sourceUrl, List<Item> items) {
    }

    private static final String RESOURCE = "data/image-credits.json";

    private final Credits credits;

    public ImageCreditsService(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.credits = mapper.readValue(in, Credits.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, ex);
        }
    }

    public Credits getCredits() {
        return credits;
    }
}
