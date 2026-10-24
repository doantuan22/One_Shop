package com.oneshop.mapper;

import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.entity.StoreProduct;
import org.springframework.stereotype.Component;

/** The two views of a StoreProduct: the Client one never carries the exact quantity (BR-15). */
@Component
public class StoreProductMapper {

    public StoreAvailabilityResponse toAvailability(StoreProduct storeProduct) {
        boolean inStock = (storeProduct.getStatus() == com.oneshop.entity.ActiveStatus.ACTIVE)
                && (storeProduct.getStore() == null || storeProduct.getStore().getStatus() == com.oneshop.entity.ActiveStatus.ACTIVE)
                && ((storeProduct.getQuantity() - storeProduct.getReservedQuantity()) > 0);
        return new StoreAvailabilityResponse(storeProduct.getId(), storeProduct.getStore().getId(),
                storeProduct.getStore().getName(), storeProduct.getProduct().getPrice(), inStock);
    }

    public StoreProductStockResponse toStockView(StoreProduct storeProduct) {
        // read-only online price of the SKU (Product.price); the legacy StoreProduct.price column is never consulted.
        return new StoreProductStockResponse(storeProduct.getId(), storeProduct.getStore().getId(),
                storeProduct.getStore().getName(), storeProduct.getProduct().getId(),
                storeProduct.getProduct().getSku(), storeProduct.getProduct().getName(), storeProduct.getProduct().getPrice(),
                storeProduct.getQuantity(), storeProduct.getStatus());
    }
}
