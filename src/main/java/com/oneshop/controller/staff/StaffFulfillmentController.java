package com.oneshop.controller.staff;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.FulfillmentQueueFilter;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;

/**
 * Staff work queue and fixed fulfillment POSTs (V3-BE-04). The unit is one Fulfillment of the Staff's Store; no route
 * accepts an Order status or a Store as authority. The Store scope and every business rule live in
 * {@link StaffFulfillmentService}; this class only binds request parameters.
 *
 * <p>Old order-level URLs ({@code /staff/orders/**}, {@code /staff/pickup}) no longer change anything: the list URL
 * shows the same queue, the rest redirect to it.
 */
@Controller
public class StaffFulfillmentController {

    private final StaffFulfillmentService fulfillments;

    public StaffFulfillmentController(StaffFulfillmentService fulfillments) {
        this.fulfillments = fulfillments;
    }

    @GetMapping({"/staff/fulfillments", "/staff/orders"})
    public String queue(@RequestParam(required = false) Long storeId,
                        @RequestParam(required = false) OrderFulfillmentStatus status,
                        @RequestParam(required = false) FulfillmentType type,
                        @RequestParam(required = false) Long orderId,
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to,
                        @RequestParam(defaultValue = "true") boolean open,
                        @RequestParam(defaultValue = "0") int page, HttpServletRequest request, Model model) {
        // one of two fixed paths, never a request value, so the pagination links cannot be steered
        String base = "/staff/orders".equals(request.getRequestURI()) ? "/staff/orders" : "/staff/fulfillments";
        return show(new FulfillmentQueueFilter(storeId, status, type, orderId, StaffFulfillmentService.parseDate(from),
                StaffFulfillmentService.parseDate(to), open && status == null), page, null, base, model);
    }

    @GetMapping("/staff/fulfillments/delivery")
    public String deliveryQueue(@RequestParam(defaultValue = "0") int page, Model model) {
        return show(new FulfillmentQueueFilter(null, null, FulfillmentType.DELIVERY, null, null, null, true), page,
                FulfillmentType.DELIVERY, "/staff/fulfillments/delivery", model);
    }

    @GetMapping("/staff/fulfillments/pickup")
    public String pickupQueue(@RequestParam(defaultValue = "0") int page, Model model) {
        return show(new FulfillmentQueueFilter(null, null, FulfillmentType.STORE_PICKUP, null, null, null, true),
                page, FulfillmentType.STORE_PICKUP, "/staff/fulfillments/pickup", model);
    }

    @GetMapping("/staff/fulfillments/{fulfillmentId:\\d+}")
    public String detail(@PathVariable Long fulfillmentId, Model model) {
        model.addAttribute("detail", fulfillments.getFulfillment(fulfillmentId));
        return "staff/fulfillments/detail";
    }

    /**
     * One POST per step: the URL segment names the action ({@code prepare}, {@code pack}, {@code ship},
     * {@code ready-for-pickup}, {@code complete-delivery}, {@code complete-pickup}, {@code fail-delivery},
     * {@code fail-pickup}) or {@code handover}, which completes either type. Only the three optional inputs below
     * are read from the request.
     */
    @PostMapping("/staff/fulfillments/{fulfillmentId:\\d+}/{actionPath}")
    public String act(@PathVariable Long fulfillmentId, @PathVariable String actionPath,
                      @RequestParam(required = false) String trackingCode,
                      @RequestParam(required = false) String pickupCode,
                      @RequestParam(required = false) String reason) {
        if ("handover".equals(actionPath)) {
            fulfillments.handover(fulfillmentId, pickupCode);
            return "redirect:/staff/fulfillments/" + fulfillmentId;
        }
        FulfillmentAction action = FulfillmentAction.fromPath(actionPath);
        if (action == null) {
            throw new ResourceNotFoundException("Không tìm thấy thao tác xử lý.");
        }
        String input = switch (action) {
            case SHIP -> trackingCode;
            case COMPLETE_PICKUP -> pickupCode;
            case FAIL_DELIVERY, FAIL_PICKUP -> reason;
            default -> null;
        };
        fulfillments.perform(fulfillmentId, action, input);
        return "redirect:/staff/fulfillments/" + fulfillmentId;
    }

    // ---- V2 URLs: read-only redirects, never a mutation ----

    @GetMapping("/staff/orders/{orderId:\\d+}")
    public String legacyOrder(@PathVariable Long orderId) {
        return "redirect:/staff/fulfillments?orderId=" + orderId + "&open=false";
    }

    @GetMapping("/staff/orders/delivery")
    public String legacyDelivery() {
        return "redirect:/staff/fulfillments/delivery";
    }

    @GetMapping({"/staff/orders/pickup", "/staff/pickup"})
    public String legacyPickup() {
        return "redirect:/staff/fulfillments/pickup";
    }

    private String show(FulfillmentQueueFilter filter, int page, FulfillmentType fixedType, String basePath,
                        Model model) {
        model.addAttribute("queue", fulfillments.getQueue(filter, page));
        model.addAttribute("basePath", basePath);
        model.addAttribute("filter", filter);
        model.addAttribute("fixedType", fixedType);
        model.addAttribute("statuses", OrderFulfillmentStatus.values());
        model.addAttribute("today", LocalDate.now());
        return "staff/fulfillments/index";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Bạn không được phân công xử lý phần giao hàng của chi nhánh này.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView database(DataAccessException ex) {
        return error(HttpStatus.CONFLICT,
                "Không thể lưu kết quả hoặc phần giao đang được xử lý. Vui lòng tải lại trang và thử lại.");
    }

    private static ModelAndView error(HttpStatus status, String message) {
        ModelAndView view = new ModelAndView("error-message");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message);
        return view;
    }
}
