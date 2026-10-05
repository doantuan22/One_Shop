package com.oneshop.repository;

import com.oneshop.entity.Product;
import com.oneshop.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "brand"})
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    /**
     * Chain-wide catalog of the Client: SKUs that are ACTIVE and whose Category and Brand are ACTIVE, whether or not a
     * Store sells them at the moment.
     *
     * @param keyword LIKE pattern (already lower-cased and escaped with {@code \}) matched on name and SKU; pass
     *                {@code "%"} for no keyword
     */
    @Query(value = """
            select p from Product p join fetch p.category c join fetch p.brand b
            where p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and (:categoryId is null or c.id = :categoryId)
              and (:brandId is null or b.id = :brandId)
              and (lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\')
            order by p.name, p.id""",
            countQuery = """
            select count(p) from Product p join p.category c join p.brand b
            where p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and (:categoryId is null or c.id = :categoryId)
              and (:brandId is null or b.id = :brandId)
              and (lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\')""")
    Page<Product> searchVisible(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                                @Param("brandId") Long brandId, Pageable pageable);

    /** One SKU as the Client may see it: ACTIVE, in an ACTIVE Category and Brand. */
    @Query("""
            select p from Product p join fetch p.category c join fetch p.brand b
            where p.id = :id
              and p.status = com.oneshop.entity.ProductStatus.ACTIVE
              and c.status = com.oneshop.entity.VisibilityStatus.ACTIVE
              and b.status = com.oneshop.entity.VisibilityStatus.ACTIVE""")
    Optional<Product> findVisibleById(@Param("id") Long id);

    /** Admin list: every status. */
    @Query(value = """
            select p from Product p join fetch p.category c join fetch p.brand b
            where lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\'
            order by p.name, p.id""",
            countQuery = """
            select count(p) from Product p
            where lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\'""")
    Page<Product> searchAll(@Param("keyword") String keyword, Pageable pageable);

    /** Choices of the Admin StoreProduct form. */
    List<Product> findByStatusNotOrderByName(ProductStatus status);
}
