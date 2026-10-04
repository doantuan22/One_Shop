package com.oneshop.mapper;

import com.oneshop.dto.response.ProductResponse;
import com.oneshop.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    /** Needs {@code category} and {@code brand} to be loaded (repository entity graph) or an open session. */
    public ProductResponse toResponse(Product product, String imageUrl) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getCategory().getName(), product.getBrand().getName(), imageUrl);
    }
}
