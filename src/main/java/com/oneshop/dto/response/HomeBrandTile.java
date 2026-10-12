package com.oneshop.dto.response;

/** A brand tile of the home page: name and how many visible products it has (no stock involved). */
public record HomeBrandTile(Long id, String name, long productCount) {
}
