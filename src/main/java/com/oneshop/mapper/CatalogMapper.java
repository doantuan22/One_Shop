package com.oneshop.mapper;

import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.ProductImageResponse;
import com.oneshop.entity.Brand;
import com.oneshop.entity.Category;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductImage;
import org.springframework.stereotype.Component;

/** Category, Brand, Product (Admin view) and ProductImage to their DTOs. */
@Component
public class CatalogMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getStatus());
    }

    public BrandResponse toResponse(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName(), brand.getDescription(), brand.getLogoUrl(),
                brand.getStatus());
    }

    /** Needs {@code category} and {@code brand} to be loaded. */
    public AdminProductResponse toAdminResponse(Product product) {
        return new AdminProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getCategory().getId(), product.getCategory().getName(), product.getBrand().getId(),
                product.getBrand().getName(), product.getStatus());
    }

    public ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl(), image.isPrimary(), image.getSortOrder());
    }
}
