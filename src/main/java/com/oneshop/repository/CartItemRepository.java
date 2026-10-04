package com.oneshop.repository;

import com.oneshop.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** Everything needed to group the cart by Store when rendering. */
    @EntityGraph(attributePaths = {"storeProduct", "storeProduct.store", "storeProduct.product"})
    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndStoreProductId(Long cartId, Long storeProductId);
}
