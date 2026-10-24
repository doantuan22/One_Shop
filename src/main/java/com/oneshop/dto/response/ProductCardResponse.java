package com.oneshop.dto.response;

import java.math.BigDecimal;

/**
 * A product card of the Client (home page, reusable by listings). Never carries an exact quantity (BR-15).
 *
 * @param storeProductId   null; adding to cart names the shared Product and never a StoreProduct
 * @param price            one shared online price from Product.price
 * @param fromPrice        always false in V3: there is no per-Store online price
 * @param inStock          in stock at the selected Store, or at at least one Store of the chain
 * @param inStockStoreCount Stores of the chain that have the SKU in stock (whole chain only, otherwise 0)
 */
public record ProductCardResponse(Long productId, Long storeProductId, String name, String brandName,
                                  String categoryName, String imageUrl, BigDecimal price, boolean fromPrice,
                                  boolean inStock, int inStockStoreCount) {

    public static ProductCardResponse from(CatalogItemResponse item) {
        ProductResponse product = item.product();
        StoreAvailabilityResponse atStore = item.atSelectedStore();
        BigDecimal price = product.price();
        int inStockStores = (int) item.stores().stream().filter(StoreAvailabilityResponse::inStock).count();
        boolean inStock = atStore != null ? atStore.inStock() : (inStockStores > 0);
        Long storeProductId = atStore != null ? atStore.storeProductId() : null;
        int storeCount = atStore != null ? 0 : inStockStores;

        return new ProductCardResponse(product.id(), storeProductId, product.name(), product.brandName(),
                product.categoryName(), product.imageUrl(), price, false, inStock, storeCount);
    }
}
