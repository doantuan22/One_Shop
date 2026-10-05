package com.oneshop.dto.request;

import java.util.Locale;

/**
 * Catalog search of the Client: keyword on product name / SKU, plus Category and Brand filters. The Store is not part
 * of it on purpose: it comes from the validated selected Store, not from a free request parameter.
 */
public record ProductSearchCriteria(String keyword, Long categoryId, Long brandId) {

    public ProductSearchCriteria {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public static ProductSearchCriteria none() {
        return new ProductSearchCriteria(null, null, null);
    }

    public static ProductSearchCriteria ofKeyword(String keyword) {
        return new ProductSearchCriteria(keyword, null, null);
    }

    /**
     * The keyword as a lower-cased "contains" LIKE pattern whose wildcards are escaped with a backslash, or "%" when
     * there is no keyword. Queries using it must declare the backslash as their escape character.
     */
    public String likePattern() {
        if (keyword == null) {
            return "%";
        }
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
        return "%" + escaped + "%";
    }
}
