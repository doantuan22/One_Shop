package com.oneshop.dto.response;

import java.time.LocalDateTime;

/** A visible review. Only the reviewer's display name is exposed, never the e-mail. */
public record ReviewResponse(Long id, String reviewerName, int rating, String comment, LocalDateTime createdAt) {
}
