package com.oneshop.service.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Free-text matching shared by the catalog / inventory searches: every word typed must appear in the text, in any
 * order, ignoring case and Vietnamese accents. The lists searched this way are small (one row per product or per
 * Store and SKU), so it is done in memory instead of depending on the database collation.
 */
public final class TextSearch {

    private TextSearch() {
    }

    /** The normalised words of {@code q}; empty when there is nothing to search for. */
    public static List<String> words(String q) {
        if (q == null) {
            return List.of();
        }
        String text = normalize(q).trim();
        return text.isEmpty() ? List.of() : List.of(text.split("\\s+"));
    }

    public static String normalize(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return folded.toLowerCase(Locale.ROOT);
    }

    /** True when every word occurs in the fields (all fields are searched as one text). */
    public static boolean matchesAll(List<String> words, String... fields) {
        StringBuilder haystack = new StringBuilder();
        for (String field : fields) {
            if (field != null) {
                haystack.append(field).append(' ');
            }
        }
        String text = normalize(haystack.toString());
        return words.stream().allMatch(text::contains);
    }

    /** One page of an already filtered and ordered list. */
    public static <T> Page<T> slice(List<T> all, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(all, pageable, all.size());
        }
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }
}
