package com.oneshop.controller.staff;

import com.oneshop.service.StaffOperationsService;
import com.oneshop.dto.response.DeliveryAction;
import com.oneshop.dto.response.PickupAction;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/** Order queue/detail and fixed fulfillment POSTs; all authorization/business rules remain in services. */
@Controller
@RequestMapping("/staff/orders")
public class StaffOrderController {
    private final StaffOperationsService operations;
    public StaffOrderController(StaffOperationsService operations) { this.operations = operations; }

    @GetMapping
    public String orders(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", operations.getOrders(page));
        return "staff/orders/index";
    }

    @GetMapping("/{orderId}")
    public String order(@PathVariable Long orderId, Model model) {
        var response = operations.getOrder(orderId);
        model.addAttribute("detail", response.detail());
        model.addAttribute("customerName", response.customerName());
        model.addAttribute("deliveryAction", response.deliveryAction());
        model.addAttribute("pickupAction", response.pickupAction());
        return "staff/orders/detail";
    }

    @PostMapping("/{orderId}/delivery/prepare")
    public String prepareDelivery(@PathVariable Long orderId) { return delivery(orderId, DeliveryAction.PREPARE); }
    @PostMapping("/{orderId}/delivery/pack")
    public String packDelivery(@PathVariable Long orderId) { return delivery(orderId, DeliveryAction.PACK); }
    @PostMapping("/{orderId}/delivery/ship")
    public String shipDelivery(@PathVariable Long orderId) { return delivery(orderId, DeliveryAction.SHIP); }
    @PostMapping("/{orderId}/delivery/complete")
    public String completeDelivery(@PathVariable Long orderId) { return delivery(orderId, DeliveryAction.COMPLETE); }
    @PostMapping("/{orderId}/pickup/prepare")
    public String preparePickup(@PathVariable Long orderId) { return pickup(orderId, PickupAction.PREPARE, null); }
    @PostMapping("/{orderId}/pickup/ready")
    public String readyPickup(@PathVariable Long orderId) { return pickup(orderId, PickupAction.READY, null); }
    @PostMapping("/{orderId}/pickup/complete")
    public String completePickup(@PathVariable Long orderId, @RequestParam(required = false) String pickupCode) {
        return pickup(orderId, PickupAction.COMPLETE, pickupCode);
    }
    private String delivery(Long id, DeliveryAction action) {
        operations.performDelivery(id, action); return "redirect:/staff/orders/" + id;
    }
    private String pickup(Long id, PickupAction action, String code) {
        operations.performPickup(id, action, code); return "redirect:/staff/orders/" + id;
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Bạn không được phân công xử lý đơn hàng của chi nhánh này.");
    }
    @ExceptionHandler(DataAccessException.class)
    public ModelAndView database(DataAccessException ex) {
        return error(HttpStatus.CONFLICT, "Không thể lưu kết quả hoặc đơn đang được xử lý. Vui lòng tải lại trang và thử lại.");
    }
    private static ModelAndView error(HttpStatus status, String message) {
        var view = new ModelAndView("error-message"); view.setStatus(status);
        view.addObject("status", status.value()); view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message); return view;
    }
}
