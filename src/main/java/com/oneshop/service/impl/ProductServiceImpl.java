package com.oneshop.service.impl;

import com.oneshop.dto.response.ProductResponse;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductImage;
import com.oneshop.entity.ProductStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.ProductMapper;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductImageRepository productImageRepository,
                              ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productMapper = productMapper;
    }

    @Override
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        Page<Product> page = productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
        Map<Long, String> primaryImages = primaryImageUrls(page.getContent());
        return page.map(product -> productMapper.toResponse(product, primaryImages.get(product.getId())));
    }

    @Override
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findByIdAndStatus(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm #" + id));
        return productMapper.toResponse(product, primaryImageUrls(List.of(product)).get(product.getId()));
    }

    /** One query for the whole page instead of one per product. */
    private Map<Long, String> primaryImageUrls(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        return productImageRepository.findByProductIdInAndPrimaryTrue(products.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.toMap(image -> image.getProduct().getId(), ProductImage::getImageUrl,
                        (first, second) -> first));
    }
}
