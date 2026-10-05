package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.StoreProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    /** Catalog of one Store (BR-04): only the StoreProducts of that Store with the given status. */
    @EntityGraph(attributePaths = "product")
    List<StoreProduct> findByStoreIdAndStatus(Long storeId, ActiveStatus status);

    /** Every StoreProduct of one Store whatever its status: the Staff/Admin inventory view. */
    @EntityGraph(attributePaths = "product")
    List<StoreProduct> findByStoreId(Long storeId);

    /** Chain-wide availability of a SKU: one row per Store that sells it. */
    @EntityGraph(attributePaths = "store")
    List<StoreProduct> findByProductIdAndStatus(Long productId, ActiveStatus status);

    Optional<StoreProduct> findByStoreIdAndProductId(Long storeId, Long productId);

    // ---- Locking, prepared for Phase 8 (Checkout) and Phase 10 (stock adjust). No business logic here. ----

    /**
     * Loads one StoreProduct with {@code PESSIMISTIC_WRITE} (SQL Server: UPDLOCK, ROWLOCK). Must be called inside a
     * transaction; the row stays locked until that transaction ends (Roadmap V2 8.2).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sp from StoreProduct sp where sp.id = :id")
    Optional<StoreProduct> findByIdForUpdate(@Param("id") Long id);

    /**
     * Loads several StoreProducts with {@code PESSIMISTIC_WRITE}. Rows are always locked in ascending id order so two
     * checkouts that share SKUs cannot deadlock by locking them in opposite order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sp from StoreProduct sp where sp.id in :ids order by sp.id")
    List<StoreProduct> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
