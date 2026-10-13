package com.oneshop.service.impl;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.service.StoreAllocationService;
import com.oneshop.service.allocation.AllocationDemand;
import com.oneshop.service.allocation.AllocationEngine;
import com.oneshop.service.allocation.AllocationFailure;
import com.oneshop.service.allocation.AllocationResult;
import com.oneshop.service.allocation.DeliveryServiceAreaPolicy;
import com.oneshop.service.allocation.LockedAllocation;
import com.oneshop.service.allocation.StockOffer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class StoreAllocationServiceImpl implements StoreAllocationService {

    private final StoreProductRepository storeProductRepository;
    private final DeliveryServiceAreaPolicy areaPolicy;

    public StoreAllocationServiceImpl(StoreProductRepository storeProductRepository,
                                      DeliveryServiceAreaPolicy areaPolicy) {
        this.storeProductRepository = storeProductRepository;
        this.areaPolicy = areaPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public AllocationResult preview(Collection<AllocationDemand> demands, FulfillmentType type, Long pickupStoreId,
                                    String shippingAddress) {
        List<Long> productIds = productIds(demands);
        if (productIds.isEmpty()) {
            return AllocationResult.failure(AllocationFailure.NO_DEMAND, List.of());
        }
        List<StoreProduct> candidates = storeProductRepository.findCheckoutCandidatesByProductIds(productIds);
        return allocate(demands, candidates, type, pickupStoreId, shippingAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Store> pickupStoresCovering(Collection<AllocationDemand> demands) {
        List<Long> productIds = productIds(demands);
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<StoreProduct> candidates = storeProductRepository.findCheckoutCandidatesByProductIds(productIds);
        Map<Long, Store> stores = new LinkedHashMap<>();
        candidates.forEach(sp -> stores.putIfAbsent(sp.getStore().getId(), sp.getStore()));
        return stores.values().stream()
                .filter(Store::isPickupEnabled)
                .sorted(java.util.Comparator.comparing(Store::getName).thenComparing(Store::getId))
                .filter(store -> allocate(demands, candidates, FulfillmentType.STORE_PICKUP, store.getId(), null).success())
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedAllocation allocateForUpdate(Collection<AllocationDemand> demands, FulfillmentType type,
                                              Long pickupStoreId, String shippingAddress) {
        List<Long> productIds = productIds(demands);
        if (productIds.isEmpty()) {
            return new LockedAllocation(AllocationResult.failure(AllocationFailure.NO_DEMAND, List.of()), Map.of());
        }
        // ids only (no entity is loaded), then one locking read in ascending id order: the rows seen from here on are
        // the committed rows of this moment and nobody else can change them until this transaction ends.
        List<Long> ids = storeProductRepository.findCheckoutCandidateIdsByProductIds(productIds);
        Map<Long, StoreProduct> locked = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (StoreProduct storeProduct : storeProductRepository.findAllByIdForUpdate(ids)) {
                locked.put(storeProduct.getId(), storeProduct);
            }
        }
        List<StoreProduct> stillSellable = locked.values().stream()
                .filter(sp -> sp.isActive() && sp.getStore().isActive()
                        && sp.getProduct().getStatus() == ProductStatus.ACTIVE)
                .toList();
        return new LockedAllocation(allocate(demands, stillSellable, type, pickupStoreId, shippingAddress), locked);
    }

    private AllocationResult allocate(Collection<AllocationDemand> demands, List<StoreProduct> candidates,
                                      FulfillmentType type, Long pickupStoreId, String shippingAddress) {
        List<StockOffer> offers = candidates.stream()
                .filter(sp -> servesFulfillment(sp.getStore(), type, shippingAddress))
                .map(sp -> new StockOffer(sp.getId(), sp.getStore().getId(), sp.getProduct().getId(),
                        sp.getAvailableQuantity()))
                .toList();
        if (type == FulfillmentType.STORE_PICKUP) {
            if (pickupStoreId == null) {
                return AllocationResult.failure(AllocationFailure.NO_ELIGIBLE_STORE, List.of());
            }
            return AllocationEngine.allocateAtStore(pickupStoreId, demands, offers);
        }
        return AllocationEngine.allocate(demands, offers);
    }

    private boolean servesFulfillment(Store store, FulfillmentType type, String shippingAddress) {
        if (type == FulfillmentType.STORE_PICKUP) {
            return store.isPickupEnabled();
        }
        return store.isDeliveryEnabled() && areaPolicy.canServe(store, shippingAddress);
    }

    private static List<Long> productIds(Collection<AllocationDemand> demands) {
        return demands.stream().filter(Objects::nonNull).map(AllocationDemand::productId).filter(Objects::nonNull)
                .distinct().sorted().toList();
    }
}
