package com.oneshop.service.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure, deterministic allocation of a cart to Stores (V3 DEC-05, DEC-23). No database, no clock, no randomness: the
 * same demand and offers always give the same result.
 *
 * <p><b>Rules</b>
 * <ol>
 *   <li><b>One-Store Full Cart.</b> If at least one Store can supply every line in full, one of them is chosen: the
 *       one with the largest total available quantity of the demanded SKUs, then the smallest Store id.</li>
 *   <li><b>Multi-Store.</b> Otherwise a greedy cover. In every round the Store with the best score is taken, where the
 *       score is (lines it supplies <em>in full</em>, units it can supply, already used by this plan, total available
 *       of the missing SKUs, smallest Store id). Greedy is not guaranteed to give the minimum number of Stores and it
 *       looks at stock only, never at distance.</li>
 *   <li><b>Same-SKU quantity split (OPEN-01, decided).</b> The quantity of one SKU is split between Stores only when no
 *       single Store can still supply the whole remaining quantity of that line. Then each chosen Store gives what it
 *       has, up to the missing quantity (a Store never gives more than is missing). The line then appears in several
 *       Fulfillments, one {@link AllocatedLine} per StoreProduct.</li>
 *   <li>Stock is never moved between Stores: an offer is only ever used by the Store it belongs to, and no unit is
 *       allocated twice.</li>
 * </ol>
 * Invariant of a successful result: for every SKU, the allocated quantities add up to exactly the demanded
 * quantity, and each allocated quantity is positive and at most the available quantity of its StoreProduct.
 *
 * <p>Eligibility of Stores and StoreProducts (ACTIVE, delivery/pickup flags) is decided by the caller before it
 * builds the offers.
 */
public final class AllocationEngine {

    private AllocationEngine() {
    }

    /** Cart to any eligible Stores: One-Store priority, then Multi-Store. */
    public static AllocationResult allocate(Collection<AllocationDemand> demands, Collection<StockOffer> offers) {
        Map<Long, Integer> wanted = merge(demands);
        if (wanted.isEmpty()) {
            return AllocationResult.failure(AllocationFailure.NO_DEMAND, List.of());
        }
        Map<Long, Map<Long, StockOffer>> byStore = byStore(offers);
        if (byStore.isEmpty()) {
            return AllocationResult.failure(AllocationFailure.NO_ELIGIBLE_STORE, List.copyOf(wanted.keySet()));
        }

        // Strategy 1: a Store that supplies the whole cart
        Long best = null;
        long bestTotal = -1;
        for (Map.Entry<Long, Map<Long, StockOffer>> store : byStore.entrySet()) {
            if (coversAll(store.getValue(), wanted)) {
                long total = totalAvailable(store.getValue(), wanted.keySet());
                if (total > bestTotal) { // ascending store id order: the first of equal totals wins
                    best = store.getKey();
                    bestTotal = total;
                }
            }
        }
        if (best != null) {
            return AllocationResult.success(List.of(allocation(best, byStore.get(best), wanted)));
        }

        // Strategy 2 / 3: greedy cover, splitting the quantity of a line only when no Store can supply it in full
        Map<Long, Integer> missing = new TreeMap<>(wanted);
        Map<Long, Map<Long, Integer>> left = new TreeMap<>(); // store -> product -> still available
        byStore.forEach((storeId, offersOfStore) -> {
            Map<Long, Integer> copy = new TreeMap<>();
            offersOfStore.forEach((productId, offer) -> copy.put(productId, offer.available()));
            left.put(storeId, copy);
        });
        Map<Long, Map<Long, Integer>> taken = new TreeMap<>(); // store -> product -> allocated quantity
        while (!missing.isEmpty()) {
            Long pick = null;
            int pickFull = 0;
            long pickUnits = 0;
            boolean pickUsed = false;
            long pickTotal = -1;
            for (Long storeId : left.keySet()) {
                Map<Long, Integer> own = left.get(storeId);
                int full = 0;
                long units = 0;
                for (Map.Entry<Long, Integer> line : missing.entrySet()) {
                    int available = own.getOrDefault(line.getKey(), 0);
                    if (available >= line.getValue()) {
                        full++;
                        units += line.getValue();
                    } else if (available > 0 && mustSplit(line.getKey(), line.getValue(), left)) {
                        units += available;
                    }
                }
                if (units == 0) {
                    continue; // this Store cannot help now
                }
                boolean used = taken.containsKey(storeId);
                long total = 0;
                for (Long productId : missing.keySet()) {
                    total += own.getOrDefault(productId, 0);
                }
                if (pick == null || better(full, units, used, total, pickFull, pickUnits, pickUsed, pickTotal)) {
                    pick = storeId;
                    pickFull = full;
                    pickUnits = units;
                    pickUsed = used;
                    pickTotal = total;
                }
            }
            if (pick == null) {
                // nothing left anywhere for the missing lines: the eligible Stores simply do not have it
                return AllocationResult.failure(AllocationFailure.OUT_OF_STOCK, List.copyOf(missing.keySet()));
            }
            Map<Long, Integer> own = left.get(pick);
            Map<Long, Integer> fromThisStore = new TreeMap<>();
            for (Map.Entry<Long, Integer> line : new TreeMap<>(missing).entrySet()) {
                int available = own.getOrDefault(line.getKey(), 0);
                int quantity;
                if (available >= line.getValue()) {
                    quantity = line.getValue();
                } else if (available > 0 && mustSplit(line.getKey(), line.getValue(), left)) {
                    quantity = available;
                } else {
                    continue;
                }
                fromThisStore.put(line.getKey(), quantity);
            }
            for (Map.Entry<Long, Integer> part : fromThisStore.entrySet()) {
                own.merge(part.getKey(), -part.getValue(), Integer::sum);
                taken.computeIfAbsent(pick, id -> new TreeMap<>()).merge(part.getKey(), part.getValue(), Integer::sum);
                int remaining = missing.get(part.getKey()) - part.getValue();
                if (remaining == 0) {
                    missing.remove(part.getKey());
                } else {
                    missing.put(part.getKey(), remaining);
                }
            }
        }
        List<StoreAllocation> chosen = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Integer>> store : taken.entrySet()) {
            chosen.add(allocation(store.getKey(), byStore.get(store.getKey()), store.getValue()));
        }
        chosen.sort(Comparator.comparing(StoreAllocation::storeId));
        return AllocationResult.success(chosen);
    }

    /** True when no Store can still supply the whole remaining quantity of this line by itself. */
    private static boolean mustSplit(Long productId, int remaining, Map<Long, Map<Long, Integer>> left) {
        for (Map<Long, Integer> own : left.values()) {
            if (own.getOrDefault(productId, 0) >= remaining) {
                return false;
            }
        }
        return true;
    }

    /** Candidate beats the current best; equal scores keep the earlier (lower) Store id. */
    private static boolean better(int full, long units, boolean used, long total, int bestFull, long bestUnits,
                                  boolean bestUsed, long bestTotal) {
        if (full != bestFull) {
            return full > bestFull;
        }
        if (units != bestUnits) {
            return units > bestUnits;
        }
        if (used != bestUsed) {
            return used; // a Store already in the plan costs no extra Fulfillment
        }
        return total > bestTotal;
    }

    /** Store Pickup: the chosen Store alone must supply the whole cart. */
    public static AllocationResult allocateAtStore(Long storeId, Collection<AllocationDemand> demands,
                                                   Collection<StockOffer> offers) {
        Map<Long, Integer> wanted = merge(demands);
        if (wanted.isEmpty()) {
            return AllocationResult.failure(AllocationFailure.NO_DEMAND, List.of());
        }
        Map<Long, StockOffer> offersOfStore = byStore(offers).get(storeId);
        if (offersOfStore == null) {
            return AllocationResult.failure(AllocationFailure.NO_ELIGIBLE_STORE, List.copyOf(wanted.keySet()));
        }
        List<Long> shortProducts = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
            StockOffer offer = offersOfStore.get(line.getKey());
            if (offer == null || offer.available() < line.getValue()) {
                shortProducts.add(line.getKey());
            }
        }
        if (!shortProducts.isEmpty()) {
            return AllocationResult.failure(AllocationFailure.INSUFFICIENT_AT_PICKUP_STORE, shortProducts);
        }
        return AllocationResult.success(List.of(allocation(storeId, offersOfStore, wanted)));
    }

    // ------------------------------------------------------------------ helpers

    private static Map<Long, Integer> merge(Collection<AllocationDemand> demands) {
        Map<Long, Integer> wanted = new TreeMap<>();
        for (AllocationDemand demand : demands) {
            if (demand != null && demand.productId() != null && demand.quantity() > 0) {
                wanted.merge(demand.productId(), demand.quantity(), Integer::sum);
            }
        }
        return wanted;
    }

    /** Store id (ascending) -> product id -> offer; offers without stock are dropped. */
    private static Map<Long, Map<Long, StockOffer>> byStore(Collection<StockOffer> offers) {
        Map<Long, Map<Long, StockOffer>> byStore = new TreeMap<>();
        for (StockOffer offer : offers) {
            if (offer != null && offer.available() > 0) {
                byStore.computeIfAbsent(offer.storeId(), id -> new LinkedHashMap<>()).put(offer.productId(), offer);
            }
        }
        return byStore;
    }

    private static boolean coversAll(Map<Long, StockOffer> offersOfStore, Map<Long, Integer> wanted) {
        for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
            StockOffer offer = offersOfStore.get(line.getKey());
            if (offer == null || offer.available() < line.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static long totalAvailable(Map<Long, StockOffer> offersOfStore, Collection<Long> productIds) {
        long total = 0;
        for (Long productId : productIds) {
            StockOffer offer = offersOfStore.get(productId);
            if (offer != null) {
                total += offer.available();
            }
        }
        return total;
    }

    private static StoreAllocation allocation(Long storeId, Map<Long, StockOffer> offersOfStore,
                                              Map<Long, Integer> quantities) {
        List<AllocatedLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
            lines.add(new AllocatedLine(line.getKey(), line.getValue(), offersOfStore.get(line.getKey()).storeProductId()));
        }
        return new StoreAllocation(storeId, lines);
    }
}
