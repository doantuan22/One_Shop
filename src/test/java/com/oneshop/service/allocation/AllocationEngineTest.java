package com.oneshop.service.allocation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests of the allocation rules (V3 DEC-05, DEC-23): One-Store priority, greedy Multi-Store, same-SKU
 * quantity split, deterministic tie-break, no transfer between Stores. No Spring, no database.
 */
class AllocationEngineTest {

    private static final long A = 10L;
    private static final long B = 20L;
    private static final long C = 30L;

    private static long spId = 1000;

    private static StockOffer offer(long store, long product, int available) {
        return new StockOffer(spId++, store, product, available);
    }

    private static AllocationDemand want(long product, int quantity) {
        return new AllocationDemand(product, quantity);
    }

    private static Map<Long, Map<Long, Integer>> byStore(AllocationResult result) {
        return result.stores().stream().collect(Collectors.toMap(StoreAllocation::storeId,
                s -> s.lines().stream().collect(Collectors.toMap(AllocatedLine::productId, AllocatedLine::quantity))));
    }

    private static int totalOf(AllocationResult result, long product) {
        return result.stores().stream().flatMap(s -> s.lines().stream())
                .filter(l -> l.productId() == product).mapToInt(AllocatedLine::quantity).sum();
    }

    // ================================================================= One-Store priority

    @Test
    @DisplayName("BE03-F01 one Store with the whole cart is used: one Fulfillment")
    void oneStoreSuppliesTheWholeCart() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 2), want(B, 1), want(C, 3)), List.of(
                offer(1, A, 10), offer(1, B, 5), offer(1, C, 6),
                offer(2, A, 8), offer(2, B, 0), offer(2, C, 10)));

        assertThat(result.success()).isTrue();
        assertThat(result.fulfillmentCount()).isEqualTo(1);
        assertThat(byStore(result)).containsOnlyKeys(1L);
        assertThat(byStore(result).get(1L)).containsEntry(A, 2).containsEntry(B, 1).containsEntry(C, 3);
    }

    @Test
    @DisplayName("One Store is preferred even when several Stores together could also do it")
    void oneStoreBeatsASplit() {
        // Store 1 holds all 5 units, Stores 2 and 3 hold 3 and 2: no split is made
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 5)),
                List.of(offer(1, A, 5), offer(2, A, 3), offer(3, A, 2)));

        assertThat(byStore(result)).containsOnlyKeys(1L);
        assertThat(byStore(result).get(1L)).containsEntry(A, 5);
    }

    @Test
    @DisplayName("BE03-F09 equal candidates: more available stock wins, then the lower id, whatever the input order")
    void tieBreakIsDeterministic() {
        List<AllocationDemand> demand = List.of(want(A, 1));
        AllocationResult moreStock = AllocationEngine.allocate(demand, List.of(offer(2, A, 5), offer(1, A, 9)));
        assertThat(byStore(moreStock)).containsOnlyKeys(1L);

        List<StockOffer> offers = new ArrayList<>(List.of(offer(3, A, 7), offer(2, A, 7), offer(4, A, 7)));
        AllocationResult forward = AllocationEngine.allocate(demand, offers);
        Collections.reverse(offers);
        AllocationResult reversed = AllocationEngine.allocate(demand, offers);
        assertThat(byStore(forward)).containsOnlyKeys(2L);
        assertThat(reversed.signature()).isEqualTo(forward.signature());
    }

    // ================================================================= Multi-Store, whole lines

    @Test
    @DisplayName("Two Stores cooperate: one Order, two Fulfillments, no line split")
    void twoStoresCooperate() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 2), want(B, 1), want(C, 3)), List.of(
                offer(1, A, 2), offer(1, B, 1),
                offer(2, C, 3)));

        assertThat(result.success()).isTrue();
        assertThat(result.fulfillmentCount()).isEqualTo(2);
        assertThat(byStore(result).get(1L)).containsEntry(A, 2).containsEntry(B, 1).doesNotContainKey(C);
        assertThat(byStore(result).get(2L)).containsEntry(C, 3);
    }

    @Test
    @DisplayName("Three Stores cooperate when each holds one SKU")
    void threeStoresCooperate() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 1), want(B, 1), want(C, 1)), List.of(
                offer(1, A, 4), offer(2, B, 4), offer(3, C, 4)));

        assertThat(result.success()).isTrue();
        assertThat(result.fulfillmentCount()).isEqualTo(3);
        assertThat(result.stores()).extracting(StoreAllocation::storeId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("The Store covering more lines is taken first, the rest from another Store")
    void greedyPrefersTheStoreCoveringMostLines() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 1), want(B, 1), want(C, 1)), List.of(
                offer(1, A, 5), offer(1, B, 5),
                offer(2, B, 50),
                offer(3, C, 5)));

        assertThat(result.fulfillmentCount()).isEqualTo(2);
        assertThat(byStore(result).get(1L)).containsKeys(A, B);
        assertThat(byStore(result).get(3L)).containsKey(C);
        assertThat(byStore(result)).doesNotContainKey(2L);
    }

    // ================================================================= same-SKU quantity split (OPEN-01)

    @Test
    @DisplayName("BE03-F02 same SKU split between two Stores: 5 = 3 + 2, one Fulfillment each")
    void sameSkuIsSplitBetweenTwoStores() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 5)),
                List.of(offer(1, A, 3), offer(2, A, 2)));

        assertThat(result.success()).isTrue();
        assertThat(result.fulfillmentCount()).isEqualTo(2);
        assertThat(byStore(result).get(1L)).containsEntry(A, 3);
        assertThat(byStore(result).get(2L)).containsEntry(A, 2);
        assertThat(totalOf(result, A)).isEqualTo(5);
    }

    @Test
    @DisplayName("BE03-F03 same SKU over three Stores; a Store never gives more than is missing; not every Store is used")
    void sameSkuIsSplitOverThreeStoresWithoutOverTaking() {
        // A x8: Store 3 gives its 6, then 2 of Store 1's 4 (Store 2 is not needed)
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 8)),
                List.of(offer(1, A, 4), offer(2, A, 3), offer(3, A, 6)));
        assertThat(result.success()).isTrue();
        assertThat(byStore(result)).containsOnlyKeys(1L, 3L);
        assertThat(byStore(result).get(3L)).containsEntry(A, 6);
        assertThat(byStore(result).get(1L)).containsEntry(A, 2);

        // three Stores are all needed: 2 + 2 + 1
        AllocationResult three = AllocationEngine.allocate(List.of(want(A, 5)),
                List.of(offer(1, A, 2), offer(2, A, 2), offer(3, A, 2)));
        assertThat(byStore(three)).containsOnlyKeys(1L, 2L, 3L);
        assertThat(totalOf(three, A)).isEqualTo(5);
        assertThat(byStore(three).get(3L)).containsEntry(A, 1);
    }

    @Test
    @DisplayName("BE03-F04 several SKUs, one of them needs a split")
    void oneSkuOfSeveralNeedsASplit() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 5), want(B, 1)),
                List.of(offer(1, A, 3), offer(1, B, 1), offer(2, A, 2)));

        assertThat(result.success()).isTrue();
        assertThat(byStore(result).get(1L)).containsEntry(A, 3).containsEntry(B, 1);
        assertThat(byStore(result).get(2L)).containsEntry(A, 2);
    }

    @Test
    @DisplayName("A line is NOT split when one Store can supply it in full")
    void noNeedlessSplit() {
        // A x2: Store 1 has 1, Store 2 has 2 -> Store 2 supplies it whole; B only at Store 1
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 2), want(B, 1)),
                List.of(offer(1, A, 1), offer(1, B, 1), offer(2, A, 2)));

        assertThat(byStore(result).get(1L)).doesNotContainKey(A).containsEntry(B, 1);
        assertThat(byStore(result).get(2L)).containsEntry(A, 2);
        assertThat(result.fulfillmentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("BE03-F05 the eligible Stores together are short: nothing is allocated")
    void totalShortageFailsTheWholeCart() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 5), want(B, 1)),
                List.of(offer(1, A, 2), offer(2, A, 2), offer(1, B, 1)));

        assertThat(result.success()).isFalse();
        assertThat(result.failure()).isEqualTo(AllocationFailure.OUT_OF_STOCK);
        assertThat(result.unfulfilledProductIds()).containsExactly(A);
        assertThat(result.stores()).isEmpty();
    }

    @Test
    @DisplayName("Reserved stock is not available: only quantity - reserved counts (caller passes available)")
    void onlyAvailableQuantityCounts() {
        // available = quantity(10) - reserved(9) = 1 < 2
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 2)), List.of(offer(1, A, 1)));
        assertThat(result.success()).isFalse();
        assertThat(result.failure()).isEqualTo(AllocationFailure.OUT_OF_STOCK);
        assertThat(result.stores()).isEmpty();
        assertThat(result.unfulfilledProductIds()).containsExactly(A);
    }

    @Test
    @DisplayName("A SKU out of stock everywhere fails; nothing is allocated")
    void outOfStockFailsWholeCart() {
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 1), want(B, 1)),
                List.of(offer(1, A, 5), offer(1, B, 0), offer(2, A, 5)));

        assertThat(result.success()).isFalse();
        assertThat(result.failure()).isEqualTo(AllocationFailure.OUT_OF_STOCK);
        assertThat(result.unfulfilledProductIds()).containsExactly(B);
        assertThat(result.stores()).isEmpty();
    }

    @Test
    @DisplayName("BE03-F12 no StoreProduct is used twice in one plan and no unit is allocated twice")
    void noStoreProductIsUsedTwice() {
        List<StockOffer> offers = List.of(offer(1, A, 3), offer(1, B, 2), offer(2, A, 4), offer(2, C, 5),
                offer(3, B, 1), offer(3, C, 1));
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 6), want(B, 3), want(C, 6)), offers);

        assertThat(result.success()).isTrue();
        List<Long> used = result.stores().stream().flatMap(s -> s.lines().stream())
                .map(AllocatedLine::storeProductId).toList();
        assertThat(used).doesNotHaveDuplicates();
        assertThat(totalOf(result, A)).isEqualTo(6);
        assertThat(totalOf(result, B)).isEqualTo(3);
        assertThat(totalOf(result, C)).isEqualTo(6);
    }

    @Test
    @DisplayName("Invariants hold for 300 generated carts: totals equal, never above stock, success iff stock suffices")
    void invariantsOnGeneratedData() {
        Random random = new Random(20261013L);
        for (int round = 0; round < 300; round++) {
            List<StockOffer> offers = new ArrayList<>();
            Map<Long, Integer> chain = new HashMap<>();
            for (long store = 1; store <= 5; store++) {
                for (long product = 1; product <= 4; product++) {
                    if (random.nextInt(3) > 0) {
                        int available = random.nextInt(6);
                        offers.add(offer(store, product, available));
                        chain.merge(product, available, Integer::sum);
                    }
                }
            }
            List<AllocationDemand> demand = new ArrayList<>();
            for (long product = 1; product <= 4; product++) {
                if (random.nextBoolean()) {
                    demand.add(want(product, 1 + random.nextInt(9)));
                }
            }
            if (demand.isEmpty()) {
                continue;
            }
            AllocationResult result = AllocationEngine.allocate(demand, offers);
            boolean feasible = demand.stream().allMatch(d -> chain.getOrDefault(d.productId(), 0) >= d.quantity());
            assertThat(result.success()).as("round %d feasible=%s", round, feasible).isEqualTo(feasible);
            if (!result.success()) {
                assertThat(result.stores()).isEmpty();
                continue;
            }
            Map<Long, StockOffer> byId = offers.stream().collect(Collectors.toMap(StockOffer::storeProductId, o -> o));
            assertThat(result.stores()).extracting(StoreAllocation::storeId).doesNotHaveDuplicates();
            for (AllocationDemand d : demand) {
                assertThat(totalOf(result, d.productId())).as("round %d product %d", round, d.productId())
                        .isEqualTo(d.quantity());
            }
            for (StoreAllocation store : result.stores()) {
                for (AllocatedLine line : store.lines()) {
                    StockOffer source = byId.get(line.storeProductId());
                    assertThat(line.quantity()).isPositive().isLessThanOrEqualTo(source.available());
                    assertThat(source.storeId()).isEqualTo(store.storeId());
                    assertThat(source.productId()).isEqualTo(line.productId());
                }
            }
            List<StockOffer> reversed = new ArrayList<>(offers);
            Collections.reverse(reversed);
            assertThat(AllocationEngine.allocate(demand, reversed).signature()).isEqualTo(result.signature());
        }
    }

    // ================================================================= no transfer, pickup, edge cases

    @Test
    @DisplayName("Stock of one Store is never used for another Store (no cross-store transfer)")
    void noTransferBetweenStores() {
        List<StockOffer> offers = List.of(offer(1, A, 5), offer(2, B, 5));
        AllocationResult result = AllocationEngine.allocate(List.of(want(A, 1), want(B, 1)), offers);

        for (StoreAllocation allocation : result.stores()) {
            for (AllocatedLine line : allocation.lines()) {
                StockOffer used = offers.stream().filter(o -> o.storeProductId().equals(line.storeProductId()))
                        .findFirst().orElseThrow();
                assertThat(used.storeId()).isEqualTo(allocation.storeId());
                assertThat(used.productId()).isEqualTo(line.productId());
                assertThat(line.quantity()).isLessThanOrEqualTo(used.available());
            }
        }
    }

    @Test
    @DisplayName("Pickup: the chosen Store alone must hold the whole cart, even if other Stores could complete it")
    void pickupNeedsTheChosenStoreToHoldEverything() {
        List<StockOffer> offers = List.of(offer(1, A, 5), offer(1, B, 5), offer(2, A, 5), offer(2, B, 0), offer(3, B, 9));

        AllocationResult ok = AllocationEngine.allocateAtStore(1L, List.of(want(A, 1), want(B, 1)), offers);
        assertThat(ok.success()).isTrue();
        assertThat(ok.fulfillmentCount()).isEqualTo(1);

        // Store 2 lacks B although Store 3 has it: no source is borrowed
        AllocationResult shortAtStore = AllocationEngine.allocateAtStore(2L, List.of(want(A, 1), want(B, 1)), offers);
        assertThat(shortAtStore.success()).isFalse();
        assertThat(shortAtStore.failure()).isEqualTo(AllocationFailure.INSUFFICIENT_AT_PICKUP_STORE);
        assertThat(shortAtStore.unfulfilledProductIds()).containsExactly(B);

        // quantity of one SKU is not split for a pickup either
        AllocationResult noSplit = AllocationEngine.allocateAtStore(1L, List.of(want(A, 8)), offers);
        assertThat(noSplit.success()).isFalse();
    }

    @Test
    @DisplayName("Pickup at a Store that is not eligible (no offers) is refused")
    void pickupAtUnknownStoreIsRefused() {
        AllocationResult result = AllocationEngine.allocateAtStore(99L, List.of(want(A, 1)), List.of(offer(1, A, 5)));
        assertThat(result.success()).isFalse();
        assertThat(result.failure()).isEqualTo(AllocationFailure.NO_ELIGIBLE_STORE);
    }

    @Test
    @DisplayName("No demand and no eligible Store are distinct failures")
    void emptyInputs() {
        assertThat(AllocationEngine.allocate(List.of(), List.of(offer(1, A, 1))).failure())
                .isEqualTo(AllocationFailure.NO_DEMAND);
        assertThat(AllocationEngine.allocate(List.of(want(A, 1)), List.of()).failure())
                .isEqualTo(AllocationFailure.NO_ELIGIBLE_STORE);
    }

    @Test
    @DisplayName("Same demand and offers always give the same signature; any change of source or quantity changes it")
    void signatureIdentifiesThePlan() {
        List<AllocationDemand> demand = List.of(want(A, 2), want(B, 1));
        List<StockOffer> offers = List.of(offer(1, A, 5), offer(2, B, 5));
        String first = AllocationEngine.allocate(demand, offers).signature();
        assertThat(AllocationEngine.allocate(demand, offers).signature()).isEqualTo(first);

        // B now comes from store 3 instead: the customer must confirm again
        List<StockOffer> changed = List.of(offers.get(0), offer(3, B, 5));
        assertThat(AllocationEngine.allocate(demand, changed).signature()).isNotEqualTo(first);

        // a split source change (3+2 -> 2+3) changes the signature too
        AllocationResult split = AllocationEngine.allocate(List.of(want(A, 5)), List.of(offer(1, A, 3), offer(2, A, 2)));
        AllocationResult other = AllocationEngine.allocate(List.of(want(A, 5)), List.of(offer(1, A, 2), offer(2, A, 3)));
        assertThat(split.signature()).isNotEqualTo(other.signature());
    }
}
