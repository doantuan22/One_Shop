package com.oneshop.dto.response;

/** A category tile of the home page. {@code imageUrl} is a path of the bundled static images, or {@code null}. */
public record HomeCategoryTile(Long id, String name, String imageUrl) {
}
