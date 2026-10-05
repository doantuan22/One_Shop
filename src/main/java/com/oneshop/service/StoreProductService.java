package com.oneshop.service;

import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.request.StoreProductRequest;
import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Availability, price and stock of a SKU per Store. StoreProduct is the source of truth for all three (BR-02).
 *
 * <p>A SKU is "on sale" at a Store only when the StoreProduct is ACTIVE, the Store is ACTIVE and the SKU itself is
 * visible to customers. The Client views never carry the exact quantity, only in stock / out of stock (BR-15); the
 * Admin view does. Quantity changes go through {@link InventoryService} so that each one leaves an InventoryMovement.
 */
public interface StoreProductService {

    // ---- Client ----

    /**
     * Catalog of the whole chain (no Store selected, BR-04): one item per SKU, each with the Stores selling it and
     * their price / availability. A visible SKU that no Store sells is still listed, with an empty Store list.
     */
    Page<CatalogItemResponse> getChainCatalog(ProductSearchCriteria criteria, Pageable pageable);

    /**
     * Catalog of one Store (BR-04): only the SKUs on sale at that Store, with that Store's own price and
     * availability. Empty for a Store that is unknown or INACTIVE.
     */
    Page<CatalogItemResponse> getStoreCatalog(Long storeId, ProductSearchCriteria criteria, Pageable pageable);

    /**
     * Product Detail (Roadmap V2 9.1): the SKU, its price / availability at the selected Store and at the other
     * Stores selling it.
     *
     * @param selectedStoreId the validated selected Store, or {@code null} when browsing the whole chain
     * @throws com.oneshop.exception.ResourceNotFoundException if the product does not exist or is not visible
     */
    ProductDetailResponse getProductDetail(Long productId, Long selectedStoreId);

    // ---- Admin (exact quantity, every status) ----

    /** @param storeId   optional filter
     *  @param productId optional filter */
    Page<StoreProductStockResponse> searchStoreProducts(Long storeId, Long productId, Pageable pageable);

    StoreProductStockResponse getStoreProduct(Long id);

    /**
     * Puts a SKU on sale at a Store. A (Store, Product) pair can exist only once.
     *
     * @param adminEmail authenticated Admin, recorded on the InventoryMovement of the opening quantity
     * @throws com.oneshop.exception.BadRequestException if the pair already exists or Store/Product is unknown
     */
    StoreProductStockResponse createStoreProduct(StoreProductRequest request, String adminEmail);

    /** Changes price, status and quantity. Store and Product of an existing StoreProduct never change. */
    StoreProductStockResponse updateStoreProduct(Long id, StoreProductRequest request, String adminEmail);
}
