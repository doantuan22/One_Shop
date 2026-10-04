package com.oneshop.service;

import com.oneshop.dto.response.StoreResponse;

import java.util.List;

/**
 * Store Finder and Store metadata. Phase 6 adds the province/area filter, selectedStoreId handling and Admin CRUD.
 */
public interface StoreService {

    List<StoreResponse> getActiveStores();

    StoreResponse getStore(Long id);
}
