package com.oneshop.mapper;

import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.entity.StoreProduct;
import org.springframework.stereotype.Component;

/** The two views of a StoreProduct: the Client one never carries the exact quantity (BR-15). */
@Component
public class StoreProductMapper {

    public StoreAvailabilityResponse toAvailability(StoreProduct storeProduct) {
        return new StoreAvailabilityResponse(storeProduct.getId(), storeProduct.getStore().getId(),
                storeProduct.getStore().getName(), storeProduct.getPrice(), storeProduct.getQuantity() > 0);
    }

    public StoreProductStockResponse toStockView(StoreProduct storeProduct) {
        return new StoreProductStockResponse(storeProduct.getId(), storeProduct.getStore().getId(),
                storeProduct.getStore().getName(), storeProduct.getProduct().getId(),
                storeProduct.getProduct().getSku(), storeProduct.getProduct().getName(), storeProduct.getPrice(),
                storeProduct.getQuantity(), storeProduct.getStatus());
    }
}
