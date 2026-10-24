package com.oneshop.service;

import com.oneshop.dto.request.StockAdjustRequest;
import com.oneshop.dto.response.InventoryMovementLogResponse;
import com.oneshop.dto.response.InventoryMovementResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.dto.response.StaffInventoryHistoryResponse;
import com.oneshop.entity.InventoryMovement;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.StoreProductMapper;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.StaffStoreAssignmentRepository;
import com.oneshop.repository.StoreProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Staff stock console: reads stay inside the ACTIVE assignment scope, writes re-check the locked Store. The
 * per-product history, the central movement log and the adjust flow share the same writer; InventoryService
 * remains the stock/movement writer.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class StaffInventoryService {

    /** Live counters of the stock console; nothing is hard-coded, values are summed per assigned Store. */
    public record StockStats(long total, long low, long out, int lowThreshold) { }

    private final StaffStoreScopeService scope;
    private final InventoryService inventory;
    private final StoreProductRepository products;
    private final InventoryMovementRepository movements;
    private final StaffStoreAssignmentRepository assignments;
    private final StoreProductMapper mapper;

    public StaffInventoryService(StaffStoreScopeService scope, InventoryService inventory, StoreProductRepository products,
                                 InventoryMovementRepository movements, StaffStoreAssignmentRepository assignments,
                                 StoreProductMapper mapper) {
        this.scope = scope; this.inventory = inventory; this.products = products; this.movements = movements;
        this.assignments = assignments; this.mapper = mapper;
    }

    public Page<StoreProductStockResponse> getStock(int page) {
        return getStock(page, null, null, null);
    }

    /**
     * Stock console of the assigned Stores, narrowed by an optional SKU / product / Store keyword, a quantity
     * level (out = 0, low = 1..threshold, ok = above) and a sort (name, qty_desc, qty_asc). The fallback order is
     * the stable store-name/product-name/id ordering shared by every read-only listing.
     */
    public Page<StoreProductStockResponse> getStock(int page, String q, String level, String sort) {
        List<Long> storeIds = scope.getAssignedStoreIds();
        var words = com.oneshop.service.search.TextSearch.words(q);
        String like = "";
        String cleanLevel = cleanLevel(level);
        String cleanSort = sort == null || sort.isBlank() ? "" : sort.trim();
        if (!words.isEmpty()) {
            var all = products.searchForStaffStores(storeIds, like, cleanLevel, StaffOperationsService.LOW_STOCK_THRESHOLD,
                            cleanSort, org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                    .filter(sp -> com.oneshop.service.search.TextSearch.matchesAll(words, sp.getProduct().getSku(),
                            sp.getProduct().getName(), sp.getStore().getName()))
                    .toList();
            return com.oneshop.service.search.TextSearch.slice(all, com.oneshop.service.query.Paging.page(page, 20)).map(mapper::toStockView);
        }
        boolean plain = like.isEmpty() && cleanLevel.equals("all") && !cleanSort.equals("qty_desc") && !cleanSort.equals("qty_asc");
        Page<com.oneshop.entity.StoreProduct> rows = plain
                ? products.findByStoreIdInOrderByStoreNameAscProductNameAscIdAsc(storeIds,
                        com.oneshop.service.query.Paging.page(page, 20))
                : products.searchForStaffStores(storeIds, like, cleanLevel, StaffOperationsService.LOW_STOCK_THRESHOLD,
                        cleanSort, com.oneshop.service.query.Paging.page(page, 20));
        return rows.map(mapper::toStockView);
    }

    /** Live chips of the stock console over every assigned Store (ACTIVE rows only, like the dashboard metrics). */
    public StockStats stockStats() {
        List<Long> storeIds = scope.getAssignedStoreIds();
        int threshold = StaffOperationsService.LOW_STOCK_THRESHOLD;
        long low = 0, out = 0;
        for (Long storeId : storeIds) {
            low += products.countLowByStoreId(storeId, threshold);
            out += products.countOutByStoreId(storeId);
        }
        long total = storeIds.isEmpty() ? 0 : products.countByStoreIdIn(storeIds);
        return new StockStats(total, low, out, threshold);
    }

    /** The STAFF accounts with an ACTIVE assignment in the scope; the recorder filter of the central log.
     *  One Staff may hold several assignments, so the view dedupes by id in the SQL-side name order. */
    public List<com.oneshop.entity.User> staffOptions() {
        List<Long> storeIds = scope.getAssignedStoreIds();
        if (storeIds.isEmpty()) {
            return List.of();
        }
        var seen = new java.util.LinkedHashSet<Long>();
        List<com.oneshop.entity.User> options = new java.util.ArrayList<>();
        for (var staff : assignments.findActiveStaffOfStores(storeIds)) {
            if (seen.add(staff.getId())) options.add(staff);
        }
        return options;
    }

    /**
     * Central movement log across every assigned Store, newest first; optional keyword (SKU / product / Store /
     * note / exact Order id), movement type, recording Staff and an inclusive date range. System cancellation
     * rows keep a null recorder, exactly like the per-product history.
     */
    public Page<InventoryMovementLogResponse> getMovementLog(int page, String q, InventoryMovementType type,
                                                             Long staffId, LocalDate from, LocalDate to) {
        List<Long> storeIds = scope.getAssignedStoreIds();
        String text = q == null ? "" : q.trim();
        boolean numeric = text.matches("\\d+");
        String like = "";
        String query = numeric ? text : "";
        var range = com.oneshop.service.query.SqlDayRange.of(from, to);
        LocalDateTime fromAt = range.fromInclusive();
        LocalDateTime toAt = range.toExclusive();
        var words = numeric ? java.util.List.<String>of() : com.oneshop.service.search.TextSearch.words(text);
        if (!words.isEmpty()) {
            var all = movements.searchLog(storeIds, type, staffId, "", like, fromAt, toAt,
                            org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                    .filter(m -> com.oneshop.service.search.TextSearch.matchesAll(words, m.getStoreProduct().getProduct().getSku(),
                            m.getStoreProduct().getProduct().getName(), m.getStoreProduct().getStore().getName(),
                            m.getNote()))
                    .toList();
            return com.oneshop.service.search.TextSearch.slice(all, com.oneshop.service.query.Paging.page(page, 20)).map(this::toLogView);
        }
        return movements.searchLog(storeIds, type, staffId, query, like, fromAt, toAt,
                        com.oneshop.service.query.Paging.page(page, 20)).map(this::toLogView);
    }

    private InventoryMovementLogResponse toLogView(InventoryMovement m) {
        var actor = m.getStaff();
        var sp = m.getStoreProduct();
        var order = m.getReferenceOrder();
        return new InventoryMovementLogResponse(m.getId(), m.getType(), sp.getId(), sp.getProduct().getSku(),
                sp.getProduct().getName(), sp.getStore().getId(), sp.getStore().getName(), m.getQuantityBefore(),
                m.getQuantityAfter(), m.getQuantityChange(), actor == null ? null : actor.getId(),
                actor == null ? "Hệ thống" : actor.getFullName(), m.getNote(), order == null ? null : order.getId(),
                m.getCreatedAt());
    }

    /** lower-case contains pattern with ! as the escape character; empty for a blank keyword. */
    private static String likePattern(String q) {
        var text = q == null ? "" : q.trim().toLowerCase();
        if (text.isEmpty()) {
            return "";
        }
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /** only the "out", "low" and "ok" levels are recognized; anything else keeps every row. */
    private static String cleanLevel(String level) {
        return level == null ? "all" : switch (level) {
            case "out", "low", "ok" -> level;
            default -> "all";
        };
    }

    public StoreProductStockResponse getStoreProduct(Long id) { return mapper.toStockView(scope.requireStoreProduct(id)); }

    public StaffInventoryHistoryResponse getHistory(Long id, int page) {
        var stock = getStoreProduct(id); // Refuse cross-Store/missing resources before reading any history.
        var history = movements.findByStoreProductIdAndStoreProductStoreIdInOrderByCreatedAtDescIdDesc(
                id, scope.getAssignedStoreIds(), com.oneshop.service.query.Paging.page(page, 20)).map(m -> {
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
