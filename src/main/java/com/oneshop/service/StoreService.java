package com.oneshop.service;

import com.oneshop.dto.request.StoreRequest;
import com.oneshop.dto.response.StoreResponse;

import java.util.List;
import java.util.Optional;

/**
 * Store Finder, Store metadata, the Store a Client has selected and the Store scope of Staff. A Store is never
 * hard-deleted: INACTIVE takes it out of sale while its history stays (BR-17).
 */
public interface StoreService {

    List<StoreResponse> getActiveStores();

    /** @throws com.oneshop.exception.ResourceNotFoundException if there is no such Store */
    StoreResponse getStore(Long id);

    // ---- Client: Store Finder and selected Store ----

    /**
     * Store Finder (Roadmap V2 6.1): Stores of every status, optionally filtered by province/city and area. A blank
     * filter means "any".
     */
    List<StoreResponse> findStores(String provinceCity, String area);

    /** Province/city values to choose from in the Store Finder. */
    List<String> getProvinceCities();

    /** Areas to choose from, limited to one province/city when it is given. */
    List<String> getAreas(String provinceCity);

    /**
     * The Store behind a selectedStoreId, if a Client may browse and buy there: it must exist and be ACTIVE. An id that
     * is {@code null}, unknown or of an INACTIVE Store gives an empty result, which means "whole chain".
     */
    Optional<StoreResponse> findSelectableStore(Long storeId);

    /** @throws com.oneshop.exception.BadRequestException if the Store cannot be selected */
    StoreResponse requireSelectableStore(Long storeId);

    // ---- Admin ----

    List<StoreResponse> getAllStores();

    /** @throws com.oneshop.exception.BadRequestException if the code is already used */
    StoreResponse createStore(StoreRequest request);

    StoreResponse updateStore(Long id, StoreRequest request);

    // ---- Staff Store scope ----

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
