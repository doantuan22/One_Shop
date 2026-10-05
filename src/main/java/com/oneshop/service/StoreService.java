package com.oneshop.service;

import com.oneshop.dto.response.StoreResponse;

import java.util.List;

/**
 * Store Finder, Store metadata and the Store scope of Staff. Phase 6 adds the province/area filter, selectedStoreId
 * handling and Admin CRUD.
 */
public interface StoreService {

    List<StoreResponse> getActiveStores();

    StoreResponse getStore(Long id);

    /**
     * Store scope of a Staff account (BR-14): the ACTIVE Stores it has an ACTIVE assignment to. Empty when the account
     * has no valid assignment, in which case it must not see any Store data.
     *
     * @param staffEmail e-mail of the authenticated user (from the security context, never from the request)
     */
    List<StoreResponse> getAssignedStores(String staffEmail);

    /**
     * Checks that a Store is inside the Staff's scope before any Store data is read or changed. The {@code storeId}
     * may come from the request (for example the Store of an Order being opened); the decision always comes from the
     * assignments in the database.
     *
     * @throws org.springframework.security.access.AccessDeniedException if the Staff is not assigned to that Store
     */
    StoreResponse requireAssignedStore(String staffEmail, Long storeId);
}
