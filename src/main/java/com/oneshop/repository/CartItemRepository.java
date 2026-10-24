package com.oneshop.repository;

import com.oneshop.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** Fetch product and storeProduct for central cart. */
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand", "storeProduct", "storeProduct.store"})
    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndStoreProductId(Long cartId, Long storeProductId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /** A line addressed through its cart, so the id of a line in another cart finds nothing. */
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand", "storeProduct", "storeProduct.store"})
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    long countByCartId(Long cartId);

    /**
     * The lines of a cart WITHOUT their StoreProducts. Checkout uses this so that the StoreProducts are first read by
     * its locking query and therefore carry the stock and price of that moment, not an earlier unlocked read.
     */
    List<CartItem> findByCartId(Long cartId);
}
