package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.StoreProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    long countByStoreId(Long storeId);

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

    boolean existsByStoreIdAndProductId(Long storeId, Long productId);

    // ---- Client catalog. "Sellable" = StoreProduct ACTIVE at an ACTIVE Store (Roadmap V2 6.1). ----

    /**
     * Where the given SKUs are on sale: one query for a whole catalog page (no N+1). Whether the SKU itself is visible
     * is decided by the product query that produced the ids.
     */
    @Query("""
            select sp from StoreProduct sp join fetch sp.store s
            where sp.product.id in :productIds
              and sp.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and s.status = com.oneshop.entity.ActiveStatus.ACTIVE
            order by s.name, s.id""")
    List<StoreProduct> findSellableByProductIds(@Param("productIds") Collection<Long> productIds);

    /**
     * Catalog of ONE Store (BR-04): only its own ACTIVE StoreProducts, of SKUs the Client may see.
     *
     * @param keyword LIKE pattern, see {@link ProductRepository#searchVisible}
     */
    @Query(value = """
            select sp from StoreProduct sp join fetch sp.store s join fetch sp.product p
              join fetch p.category c join fetch p.brand b
            where s.id = :storeId
              and s.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and sp.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and (:categoryId is null or c.id = :categoryId)
              and (:brandId is null or b.id = :brandId)
              and (lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\')
            order by p.name, p.id""",
            countQuery = """
            select count(sp) from StoreProduct sp join sp.store s join sp.product p join p.category c join p.brand b
            where s.id = :storeId
              and s.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and sp.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and (:categoryId is null or c.id = :categoryId)
              and (:brandId is null or b.id = :brandId)
              and (lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\')""")
    Page<StoreProduct> searchSellableInStore(@Param("storeId") Long storeId, @Param("keyword") String keyword,
                                             @Param("categoryId") Long categoryId, @Param("brandId") Long brandId,
                                             Pageable pageable);

    /**
     * One StoreProduct, only if a customer may buy it right now: same "on sale" rule as the catalog (StoreProduct,
     * Store, Product, Category and Brand all ACTIVE). Used when something is put into a cart.
     */
    @Query("""
            select sp from StoreProduct sp join fetch sp.store s join fetch sp.product p
              join p.category c join p.brand b
            where sp.id = :id
              and sp.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and s.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE""")
    Optional<StoreProduct> findSellableById(@Param("id") Long id);

    /** Which of the given StoreProducts are on sale right now (same rule): one query for a whole cart. */
    @Query("""
            select sp.id from StoreProduct sp join sp.store s join sp.product p join p.category c join p.brand b
            where sp.id in :ids
              and sp.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and s.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE""")
    List<Long> findSellableIdsIn(@Param("ids") Collection<Long> ids);

    // ---- Admin: every status, exact quantity. ----

    @Query(value = """
            select sp from StoreProduct sp join fetch sp.store s join fetch sp.product p
            where (:storeId is null or s.id = :storeId)
              and (:productId is null or p.id = :productId)
            order by s.name, p.name, sp.id""",
            countQuery = """
            select count(sp) from StoreProduct sp
            where (:storeId is null or sp.store.id = :storeId)
              and (:productId is null or sp.product.id = :productId)""")
    Page<StoreProduct> searchForAdmin(@Param("storeId") Long storeId, @Param("productId") Long productId,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"store", "product"})
    Optional<StoreProduct> findWithStoreAndProductById(Long id);

    /** Staff lookup: allowed Store ids are resolved by the service from ACTIVE assignments, never browser input. */
    @EntityGraph(attributePaths = {"store", "product"})
    Optional<StoreProduct> findByIdAndStoreIdIn(Long id, Collection<Long> storeIds);

    /** Exact Staff stock, all statuses, restricted to server-resolved assignments before pagination/count. */
    @EntityGraph(attributePaths = {"store", "product"})
    Page<StoreProduct> findByStoreIdInOrderByStoreNameAscProductNameAscIdAsc(Collection<Long> storeIds, Pageable pageable);

    /** Dashboard inventory counts of active StoreProducts at an assigned Store. Zero is counted separately. */
    long countByStoreIdAndStatusAndQuantityBetween(Long storeId, ActiveStatus status, int min, int max);

    long countByStoreIdAndStatusAndQuantity(Long storeId, ActiveStatus status, int quantity);

    // ---- Locking: used by stock changes (InventoryService) and by Phase 8 Checkout. ----

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
