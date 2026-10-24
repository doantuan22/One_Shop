package com.oneshop.service.review;

import com.oneshop.exception.BadRequestException;

/** Validation is repeated here so direct service calls have the same limits as MVC forms. */
public final class ReviewContent {
    private ReviewContent() { }

    public static String comment(String text) {
        return normalize(text, "comment", false);
    }

    public static String reply(String text) {
        return normalize(text, "reply", true);
    }

    private static String normalize(String text, String field, boolean required) {
        if (text != null && (text.length() > 1000 || text.chars().anyMatch(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t'))) {
            throw new BadRequestException(field, "Nội dung tối đa 1000 ký tự và không chứa ký tự điều khiển.");
        }
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            if (required) throw new BadRequestException(field, "Vui lòng nhập nội dung phản hồi.");
            return null;
        }
        return body;
    }
}
