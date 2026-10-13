package com.oneshop.service.allocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Outcome of an allocation. On success {@code stores} covers the whole demand and {@code failure} is null; on failure
 * {@code stores} is empty and {@code unfulfilledProductIds} names the SKUs that could not be covered.
 */
public record AllocationResult(List<StoreAllocation> stores, AllocationFailure failure,
                               List<Long> unfulfilledProductIds) {

    public static AllocationResult success(List<StoreAllocation> stores) {
        return new AllocationResult(List.copyOf(stores), null, List.of());
    }

    public static AllocationResult failure(AllocationFailure failure, List<Long> unfulfilledProductIds) {
        return new AllocationResult(List.of(), failure, List.copyOf(unfulfilledProductIds));
    }

    public boolean success() {
        return failure == null;
    }

    /** Number of Fulfillments the allocation will create. */
    public int fulfillmentCount() {
        return stores.size();
    }

    public boolean singleStore() {
        return success() && stores.size() == 1;
    }

    /**
     * Deterministic fingerprint of "which Store supplies which SKU in which quantity". The preview hands it to the
     * customer and place-order recomputes it: a different value means what the customer agreed to has changed and
     * they must confirm again (this is also what makes the COD split consent specific).
     */
    public String signature() {
        StringBuilder plain = new StringBuilder();
        for (StoreAllocation store : stores) {
            plain.append('S').append(store.storeId()).append(':');
            for (AllocatedLine line : store.lines()) {
                plain.append(line.productId()).append('x').append(line.quantity()).append('@')
                        .append(line.storeProductId()).append(';');
            }
            plain.append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(plain.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
