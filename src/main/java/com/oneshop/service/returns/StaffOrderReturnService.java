package com.oneshop.service.returns;

import com.oneshop.dto.response.StaffReturnItemResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderReturn;
import com.oneshop.entity.OrderReturnImage;
import com.oneshop.entity.OrderReturnItem;
import com.oneshop.entity.OrderReturnStatus;
import com.oneshop.entity.ReturnDisposition;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderReturnItemRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.InventoryService;
import com.oneshop.service.StaffStoreScopeService;
import com.oneshop.service.query.Paging;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The returned goods of an approved return, as the Staff of the delivering Store see them: only lines of their own
 * assigned Stores, and only after an Administrator approved the return. For each line Staff choose, once:
 *
 * <ul>
 *   <li><b>RESTOCKED</b>: the StoreProduct stock grows by the returned quantity ({@code RETURN_ORDER} movement);</li>
 *   <li><b>DISPOSED</b>: handled in the real world (disposal, supplier); stock and money are not touched.</li>
 * </ul>
 *
 * <p>Money is not touched here at all: the refund was recorded when the Administrator approved. When the last line is
 * handled the return becomes COMPLETED. Everything runs under the Order lock, so a repeated or concurrent choice finds
 * the line no longer PENDING and changes nothing.
 */
@Service
@PreAuthorize("hasRole('STAFF')")
@Transactional(readOnly = true)
public class StaffOrderReturnService {

    public static final int MAX_NOTE = 400;

    private final OrderReturnItemRepository items;
    private final OrderRepository orders;
    private final UserRepository users;
    private final StaffStoreScopeService scope;
    private final InventoryService inventory;
    private final Clock clock;

    public StaffOrderReturnService(OrderReturnItemRepository items, OrderRepository orders, UserRepository users,
                                   StaffStoreScopeService scope, InventoryService inventory, Clock clock) {
        this.items = items;
        this.orders = orders;
        this.users = users;
        this.scope = scope;
        this.inventory = inventory;
        this.clock = clock;
    }

    /** @param disposition PENDING for lines waiting for a decision, another value for that outcome, null for all */
    public Page<StaffReturnItemResponse> list(ReturnDisposition disposition, int page) {
        return items.findForStores(scope.getAssignedStoreIds(), disposition, Paging.page(page, 20)).map(this::view);
    }

    /** Lines of approved returns still waiting for the Staff of the assigned Stores. */
    public long waitingCount() {
        return items.countWaitingForStores(scope.getAssignedStoreIds());
    }

    @Transactional
    public StaffReturnItemResponse handle(Long returnItemId, ReturnDisposition target, String note, String staffEmail) {
        if (target != ReturnDisposition.RESTOCKED && target != ReturnDisposition.DISPOSED) {
            throw new BadRequestException("action", "Vui lòng chọn nhập lại kho hoặc xử lý.");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > MAX_NOTE) {
            throw new BadRequestException("note", "Ghi chú tối đa " + MAX_NOTE + " ký tự.");
        }
        User staff = requireStaff(staffEmail);
        // authorization on the Store of the line first, before anything is locked
        OrderReturnItem peek = items.findById(returnItemId).orElseThrow(StaffOrderReturnService::notFound);
        scope.requireAssignedStore(peek.getStore().getId());

        Long orderId = items.findOrderIdById(returnItemId).orElseThrow(StaffOrderReturnService::notFound);
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(StaffOrderReturnService::notFound);
        OrderReturnItem item = items.findByIdForUpdate(returnItemId).orElseThrow(StaffOrderReturnService::notFound);
        scope.requireAssignedStore(item.getStore().getId());
        OrderReturn request = item.getOrderReturn();
        // a line that was already handled answers the same way even after its return became COMPLETED
        if (item.getDisposition() != ReturnDisposition.PENDING) {
            throw new FulfillmentConflictException(FulfillmentConflictException.RETURN_ALREADY_HANDLED,
                    "Dòng hàng hoàn này đã được xử lý trước đó; không có thay đổi nào được thực hiện lại.");
        }
        if (request.getStatus() != OrderReturnStatus.APPROVED) {
            throw new BadRequestException("Yêu cầu trả hàng chưa được duyệt; không thể xử lý dòng hàng này.");
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        item.setDisposition(target);
        item.setHandledBy(staff);
        item.setHandledAt(now);
        item.setHandleNote(cleanNote.isEmpty() ? null : cleanNote);
        items.saveAndFlush(item);

        if (target == ReturnDisposition.RESTOCKED) {
            String stockNote = "Nhập lại kho do khách trả hàng - Order #" + order.getId() + " (yêu cầu trả hàng #"
                    + request.getId() + ")" + (cleanNote.isEmpty() ? "" : ": " + cleanNote);
            inventory.restoreReturnedItem(order, item.getStoreProduct().getId(), item.getStore().getId(),
                    item.getQuantity(), staff, stockNote.length() > 500 ? stockNote.substring(0, 500) : stockNote);
        }
        if (items.countByOrderReturnIdAndDisposition(request.getId(), ReturnDisposition.PENDING) == 0) {
            request.setStatus(OrderReturnStatus.COMPLETED);
            request.setCompletedAt(now);
        }
        return view(item);
    }

    // ------------------------------------------------------------------ helpers

    private StaffReturnItemResponse view(OrderReturnItem item) {
        OrderReturn request = item.getOrderReturn();
        return new StaffReturnItemResponse(item.getId(), request.getId(), request.getOrder().getId(),
                item.getStore().getName(), item.getProductName(), item.getStoreProduct().getProduct().getSku(),
                item.getQuantity(), item.getSubtotal(), item.getStoreProduct().getQuantity(), request.getReason(),
                request.getImages().stream().map(OrderReturnImage::getImageUrl).toList(), request.getReviewedAt(),
                item.getDisposition(),
                item.getHandledBy() == null ? null : item.getHandledBy().getFullName(), item.getHandledAt(),
                item.getHandleNote());
    }

    private User requireStaff(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.STAFF)
                .orElseThrow(() -> new AccessDeniedException("Chỉ nhân viên được xử lý hàng hoàn."));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy dòng hàng hoàn.");
    }
}
