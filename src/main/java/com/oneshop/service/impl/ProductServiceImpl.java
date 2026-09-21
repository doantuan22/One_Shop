package com.oneshop.service.impl;

import com.oneshop.dto.response.ProductResponse;
import com.oneshop.entity.Product;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.ProductMapper;
import com.oneshop.repository.ProductRepository;
import com.oneshop.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Override
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(productMapper::toResponse);
    }

    @Override
    public ProductResponse getProduct(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm #" + id));
    }
}
