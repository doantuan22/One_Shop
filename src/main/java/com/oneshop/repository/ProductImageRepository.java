package com.oneshop.repository;

import com.oneshop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Primary image first, then by sort order. */
    List<ProductImage> findByProductIdOrderByPrimaryDescSortOrderAscIdAsc(Long productId);

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    List<ProductImage> findByProductIdInAndPrimaryTrue(Collection<Long> productIds);

    /** An image addressed through its Product, so an id of another Product cannot be used. */
    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
}
