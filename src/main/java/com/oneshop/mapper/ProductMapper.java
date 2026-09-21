package com.oneshop.mapper;

import com.oneshop.dto.response.ProductResponse;
import com.oneshop.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getImageUrl());
    }
}
