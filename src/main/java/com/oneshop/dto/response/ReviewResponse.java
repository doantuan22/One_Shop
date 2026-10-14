package com.oneshop.dto.response;

import java.time.LocalDateTime;

/**
 * A visible review. Only the reviewer's display name is exposed, never the e-mail. {@code reply} is the Store's
 * answer (null when none); {@code replyStoreName} is the verified OrderItem Fulfillment's Store (null for unknown source), and the
 * name of the person who answered is never exposed.
 */
public record ReviewResponse(Long id, String reviewerName, int rating, String comment, LocalDateTime createdAt,
                             String reply, LocalDateTime repliedAt, String replyStoreName) {
}
