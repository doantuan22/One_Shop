package com.oneshop.dto.response;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String description, BigDecimal price, String imageUrl) {
}
