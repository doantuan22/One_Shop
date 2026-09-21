package com.oneshop.service;

import com.oneshop.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    Page<ProductResponse> getActiveProducts(Pageable pageable);

    ProductResponse getProduct(Long id);
}
