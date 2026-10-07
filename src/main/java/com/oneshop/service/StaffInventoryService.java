package com.oneshop.service;

import com.oneshop.dto.request.StockAdjustRequest;
import com.oneshop.dto.response.*;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.StoreProductMapper;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.StoreProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Staff authorization/orchestration only; InventoryService remains the stock/movement writer. */
@Service
@Validated
@Transactional(readOnly = true)
public class StaffInventoryService {
    private final StaffStoreScopeService scope;
    private final InventoryService inventory;
    private final StoreProductRepository products;
    private final InventoryMovementRepository movements;
    private final StoreProductMapper mapper;

    public StaffInventoryService(StaffStoreScopeService scope, InventoryService inventory, StoreProductRepository products,
                                 InventoryMovementRepository movements, StoreProductMapper mapper) {
        this.scope = scope; this.inventory = inventory; this.products = products; this.movements = movements; this.mapper = mapper;
    }

    public Page<StoreProductStockResponse> getStock(int page) {
        return products.findByStoreIdInOrderByStoreNameAscProductNameAscIdAsc(scope.getAssignedStoreIds(),
                PageRequest.of(Math.max(0, page), 20)).map(mapper::toStockView);
    }

    public StoreProductStockResponse getStoreProduct(Long id) { return mapper.toStockView(scope.requireStoreProduct(id)); }

    public StaffInventoryHistoryResponse getHistory(Long id, int page) {
        var stock = getStoreProduct(id); // Refuse cross-Store/missing resources before reading any history.
        var history = movements.findByStoreProductIdAndStoreProductStoreIdInOrderByCreatedAtDescIdDesc(
                id, scope.getAssignedStoreIds(), PageRequest.of(Math.max(0, page), 20)).map(m -> {
            var actor = m.getStaff();
            return new InventoryMovementResponse(m.getId(), m.getType(), m.getQuantityBefore(), m.getQuantityAfter(),
                    m.getQuantityChange(), actor == null ? null : actor.getId(), actor == null ? "Hệ thống" : actor.getFullName(),
                    m.getNote(), m.getCreatedAt());
        });
        return new StaffInventoryHistoryResponse(stock, history);
    }

    /** Returns false for unchanged stock: the existing writer/DB reject zero-change movements. */
    @Transactional
    public boolean adjust(Long id, @NotNull @Valid StockAdjustRequest request) {
        scope.getAssignedStoreIds(); // Authenticate/resolve assignment before resource access.
        if (id == null) throw new ResourceNotFoundException("Không tìm thấy sản phẩm của chi nhánh.");
        var locked = products.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm của chi nhánh."));
        scope.requireAssignedStore(locked.getStore().getId()); // Actual locked Store, never a posted store_id.
        boolean changed = locked.getQuantity() != request.newQuantity();
        inventory.adjustStock(id, request.newQuantity(), SecurityContextHolder.getContext().getAuthentication().getName(), request.note());
        return changed;
    }
}
