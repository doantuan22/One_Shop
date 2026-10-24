package com.oneshop.service.returns;

import com.oneshop.dto.response.OrderReturnResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderReturn;
import com.oneshop.entity.OrderReturnStatus;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderReturnImageRepository;
import com.oneshop.repository.OrderReturnItemRepository;
import com.oneshop.repository.OrderReturnRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.payment.PartialRefundService;
import com.oneshop.service.query.Paging;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The Administrator decides every return request (Staff never do).
 *
 * <ul>
 *   <li><b>Approve</b>: in ONE transaction under the Order lock, the goods value is refunded in simulation at once
 *   (ledger row {@code RETURN}, shipping fee kept) and the return becomes APPROVED. Its lines then appear on the work
 *   list of the Staff of the Store that delivered each of them. A failed simulation rolls the approval back whole.</li>
 *   <li><b>Reject</b>: a reason is required; the decision is final.</li>
 * </ul>
 *
 * <p>A repeated or concurrent decision finds the return no longer REQUESTED and changes nothing but answers with a
 * conflict. The Order and Fulfillment statuses are never edited.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminOrderReturnService {

    public static final int MIN_NOTE = 5;
    public static final int MAX_NOTE = 400;

    private final OrderReturnRepository returns;
    private final OrderReturnItemRepository items;
    private final OrderReturnImageRepository images;
    private final OrderRepository orders;
    private final UserRepository users;
    private final PartialRefundService refundService;
    private final Clock clock;

    public AdminOrderReturnService(OrderReturnRepository returns, OrderReturnItemRepository items,
                                   OrderReturnImageRepository images, OrderRepository orders, UserRepository users,
                                   PartialRefundService refundService, Clock clock) {
        this.returns = returns;
        this.items = items;
        this.images = images;
        this.orders = orders;
        this.users = users;
        this.refundService = refundService;
        this.clock = clock;
    }

    public Page<OrderReturnResponse> list(OrderReturnStatus status, int page) {
        return returns.search(status, Paging.page(page, 20)).map(this::view);
    }

    public long waitingCount() {
        return returns.countByStatus(OrderReturnStatus.REQUESTED);
    }

    public OrderReturnResponse get(Long returnId) {
        return view(returns.findDetailById(returnId).orElseThrow(AdminOrderReturnService::notFound));
    }

    /** The return of one Order (for the Admin Order screen), if any. */
    public java.util.Optional<OrderReturnResponse> findByOrder(Long orderId) {
        return returns.findByOrderId(orderId).map(this::view);
    }

    @Transactional
    public OrderReturnResponse approve(Long returnId, String adminEmail) {
        User admin = requireAdmin(adminEmail);
        Order order = lockOrder(returnId);
        OrderReturn request = lockedRequest(returnId);
        requireRequested(request);
        refundService.refundReturn(order, request, request.getRefundAmount(), admin,
                "Hoàn tiền hàng trả lại - yêu cầu trả hàng #" + request.getId());
        request.setStatus(OrderReturnStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now(clock).withNano(0));
        returns.saveAndFlush(request);
        return view(request);
    }

    @Transactional
    public OrderReturnResponse reject(Long returnId, String note, String adminEmail) {
        User admin = requireAdmin(adminEmail);
        String clean = note == null ? "" : note.trim();
        if (clean.length() < MIN_NOTE || clean.length() > MAX_NOTE) {
            throw new BadRequestException("note", "Vui lòng nhập lý do từ chối từ " + MIN_NOTE + " đến " + MAX_NOTE + " ký tự.");
        }
        lockOrder(returnId);
        OrderReturn request = lockedRequest(returnId);
        requireRequested(request);
        request.setStatus(OrderReturnStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now(clock).withNano(0));
        request.setReviewNote(clean);
        returns.saveAndFlush(request);
        return view(request);
    }

    // ------------------------------------------------------------------ helpers

    /** Order lock first: the same order every payment, fulfillment and return writer uses. */
    private Order lockOrder(Long returnId) {
        Long orderId = returns.findOrderIdById(returnId).orElseThrow(AdminOrderReturnService::notFound);
        return orders.findByIdForUpdate(orderId).orElseThrow(AdminOrderReturnService::notFound);
    }

    private OrderReturn lockedRequest(Long returnId) {
        return returns.findByIdForUpdate(returnId).orElseThrow(AdminOrderReturnService::notFound);
    }

    private static void requireRequested(OrderReturn request) {
        if (request.getStatus() != OrderReturnStatus.REQUESTED) {
            throw new FulfillmentConflictException(FulfillmentConflictException.RETURN_ALREADY_DECIDED,
                    "Yêu cầu trả hàng đã được xử lý trước đó; không có thay đổi nào được thực hiện lại.");
        }
    }

    private OrderReturnResponse view(OrderReturn request) {
        return OrderReturnMapper.toResponse(request, items.findByOrderReturnIdOrderByIdAsc(request.getId()),
                images.findByOrderReturnIdOrderBySortOrderAscIdAsc(request.getId()), true);
    }

    private User requireAdmin(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được duyệt yêu cầu trả hàng."));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy yêu cầu trả hàng.");
    }
}
