package com.oneshop.mapper;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.Store;
import org.springframework.stereotype.Component;

@Component
public class StoreMapper {

    public StoreResponse toResponse(Store store) {
        return new StoreResponse(store.getId(), store.getCode(), store.getName(), store.getAddress(),
                store.getProvinceCity(), store.getArea(), store.getPhone(), store.getOpeningHours(),
                store.isDeliveryEnabled(), store.isPickupEnabled(), store.getStatus());
    }
}
