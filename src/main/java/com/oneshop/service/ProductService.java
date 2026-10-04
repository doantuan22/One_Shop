package com.oneshop.service;

import com.oneshop.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Catalog of SKUs shared by the whole chain. Category/Brand listing, SKU search and Admin CRUD arrive in Phase 6.
 */
public interface ProductService {

    Page<ProductResponse> getActiveProducts(Pageable pageable);

    ProductResponse getProduct(Long id);
}
