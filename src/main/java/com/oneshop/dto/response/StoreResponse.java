package com.oneshop.dto.response;

import com.oneshop.entity.ActiveStatus;

/** Store Finder view of a Store. */
public record StoreResponse(Long id, String code, String name, String address, String provinceCity, String area,
                            String phone, String openingHours, boolean deliveryEnabled, boolean pickupEnabled,
                            ActiveStatus status) {
}
