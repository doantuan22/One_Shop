package com.oneshop.controller.staff;

import com.oneshop.service.DeliveryFulfillmentService;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

/** Fixed POST actions only; Staff identity comes from Spring Security and scope is rechecked in the service. */
@Controller
@RequestMapping("/staff/orders/delivery")
public class StaffDeliveryController {
    private final DeliveryFulfillmentService delivery;

    public StaffDeliveryController(DeliveryFulfillmentService delivery) { this.delivery = delivery; }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("deliveryOrders", delivery.getDeliveryOrders(principal.getName()));
        return "staff/delivery/index";
    }

    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Principal principal, Model model) {
        model.addAttribute("deliveryDetail", delivery.getDeliveryOrder(principal.getName(), orderId));
        return "staff/delivery/detail";
    }

    @PostMapping("/{orderId}/prepare")
    public String prepare(@PathVariable Long orderId, Principal principal) {
        delivery.startPreparingDelivery(principal.getName(), orderId);
        return redirect(orderId);
    }

    @PostMapping("/{orderId}/pack")
    public String pack(@PathVariable Long orderId, Principal principal) {
        delivery.markDeliveryPacked(principal.getName(), orderId);
        return redirect(orderId);
    }

    @PostMapping("/{orderId}/ship")
    public String ship(@PathVariable Long orderId, Principal principal) {
        delivery.markDeliveryShipping(principal.getName(), orderId);
        return redirect(orderId);
    }

    @PostMapping("/{orderId}/complete")
    public String complete(@PathVariable Long orderId, Principal principal) {
        delivery.completeDelivery(principal.getName(), orderId);
        return redirect(orderId);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Bạn không được phân công xử lý đơn hàng của chi nhánh này.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView databaseError(DataAccessException ex) {
        return error(HttpStatus.CONFLICT, ex instanceof ConcurrencyFailureException
                ? "Đơn hàng đang được xử lý. Vui lòng tải lại trang và thử lại."
                : "Không thể lưu kết quả xử lý đơn hàng. Vui lòng tải lại trang và thử lại.");
    }

    private static ModelAndView error(HttpStatus status, String message) {
        ModelAndView view = new ModelAndView("error-message");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message);
        return view;
    }

    private static String redirect(Long id) { return "redirect:/staff/orders/delivery/" + id; }
}
