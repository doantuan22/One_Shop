package com.oneshop.mapper;

import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.entity.StoreProduct;
import org.springframework.stereotype.Component;

/** The two views of a StoreProduct: the Client one never carries the exact quantity (BR-15). */
@Component
public class StoreProductMapper {

    public StoreAvailabilityResponse toAvailability(StoreProduct storeProduct) {
        java.math.BigDecimal price = (storeProduct.getProduct() != null && storeProduct.getProduct().getPrice() != null)
                ? storeProduct.getProduct().getPrice()
                : storeProduct.getPrice();
        boolean inStock = (storeProduct.getStatus() == com.oneshop.entity.ActiveStatus.ACTIVE)
                && (storeProduct.getStore() == null || storeProduct.getStore().getStatus() == com.oneshop.entity.ActiveStatus.ACTIVE)
                && ((storeProduct.getQuantity() - storeProduct.getReservedQuantity()) > 0);
        return new StoreAvailabilityResponse(storeProduct.getId(), storeProduct.getStore().getId(),
                storeProduct.getStore().getName(), price, inStock);
    }

    public StoreProductStockResponse toStockView(StoreProduct storeProduct) {
        // read-only online price of the SKU (Product.price); the Store has no price of its own in V3
        java.math.BigDecimal price = storeProduct.getProduct().getPrice() != null
                ? storeProduct.getProduct().getPrice()
                : storeProduct.getPrice();
        return new StoreProductStockResponse(storeProduct.getId(), storeProduct.getStore().getId(),
                storeProduct.getStore().getName(), storeProduct.getProduct().getId(),
                storeProduct.getProduct().getSku(), storeProduct.getProduct().getName(), price,
                storeProduct.getQuantity(), storeProduct.getStatus());
    }
}
