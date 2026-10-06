package com.oneshop.service;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.StoreProduct;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.StoreProductRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Staff service foundation for Order, StoreProduct and Inventory callers. Identity comes only from SecurityContext;
 * StoreService remains the single assignment resolver. Supports every ACTIVE assignment, without selecting an
 * arbitrary first Store or accepting a browser-selected Store as authority.
 *
 * <p>Resource lookups are internal service primitives, not HTTP entity responses. Mutation callers must hold their
 * resource lock and recheck its actual Store with {@link #requireAssignedStore} inside their transaction.
 */
@Service
@Transactional(readOnly = true)
public class StaffStoreScopeService {
    private final StoreService stores;
    private final OrderRepository orders;
    private final StoreProductRepository products;

    public StaffStoreScopeService(StoreService stores, OrderRepository orders, StoreProductRepository products) {
        this.stores = stores;
        this.orders = orders;
        this.products = products;
    }

    /** Resolves current ACTIVE STAFF -> ACTIVE assignments -> ACTIVE Stores, afresh for each call. */
    public List<StoreResponse> getAssignedStores() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_STAFF".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Chỉ nhân viên đã đăng nhập được truy cập dữ liệu chi nhánh.");
        }
        var assigned = stores.getAssignedStores(auth.getName());
        if (assigned.isEmpty()) {
            throw new AccessDeniedException("Tài khoản chưa được phân công chi nhánh hoạt động.");
        }
        return List.copyOf(assigned);
    }

    /** These ids, never request store_id, are the allowed filter passed to Staff repositories. */
    public List<Long> getAssignedStoreIds() {
        return getAssignedStores().stream().map(StoreResponse::id).toList();
    }

    /** A resource's Store (or a requested subset) must belong to the resolved scope; this never grants assignment. */
    public StoreResponse requireAssignedStore(Long storeId) {
        return getAssignedStores().stream().filter(s -> s.id().equals(storeId)).findFirst()
                .orElseThrow(() -> new AccessDeniedException("Bạn không được phân công tại chi nhánh này."));
    }

    public Order requireOrder(Long orderId) {
        var ids = getAssignedStoreIds();
        if (orderId == null) throw new ResourceNotFoundException("Không tìm thấy đơn hàng của chi nhánh.");
        return orders.findByIdAndStoreIdIn(orderId, ids)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của chi nhánh."));
    }

    /** StoreProduct's real Store also determines scope for future Inventory operations; no inventory writes here. */
    public StoreProduct requireStoreProduct(Long storeProductId) {
        var ids = getAssignedStoreIds();
        if (storeProductId == null) throw new ResourceNotFoundException("Không tìm thấy sản phẩm của chi nhánh.");
        return products.findByIdAndStoreIdIn(storeProductId, ids)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm của chi nhánh."));
    }
}
