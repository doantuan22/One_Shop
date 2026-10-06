package com.oneshop.controller.staff;

import com.oneshop.service.PickupFulfillmentService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import java.security.Principal;

@Controller
@RequestMapping("/staff/orders/pickup")
public class StaffPickupController {
    private final PickupFulfillmentService pickup;
    public StaffPickupController(PickupFulfillmentService pickup) { this.pickup = pickup; }
    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("pickupOrders", pickup.getPickupOrders(principal.getName()));
        return "staff/pickup/index";
    }
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Principal principal, Model model) {
        model.addAttribute("orderDetail", pickup.getPickupOrder(principal.getName(), orderId));
        return "staff/pickup/detail";
    }
    @PostMapping("/{orderId}/prepare")
    public String prepare(@PathVariable Long orderId, Principal principal) {
        pickup.startPreparingPickup(principal.getName(), orderId); return redirect(orderId);
    }
    @PostMapping("/{orderId}/ready")
    public String ready(@PathVariable Long orderId, Principal principal) {
        pickup.markReadyForPickup(principal.getName(), orderId); return redirect(orderId);
    }
    @PostMapping("/{orderId}/complete")
    public String complete(@PathVariable Long orderId, @RequestParam(required = false) String pickupCode, Principal principal) {
        pickup.completePickup(principal.getName(), orderId, pickupCode); return redirect(orderId);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Bạn không được phân công xử lý đơn hàng của chi nhánh này.");
    }
    @ExceptionHandler(DataAccessException.class)
    public ModelAndView database(DataAccessException ex) {
        return error(HttpStatus.CONFLICT, "Không thể lưu kết quả nhận hàng hoặc đơn đang được xử lý. Vui lòng tải lại trang và thử lại.");
    }
    private static ModelAndView error(HttpStatus status, String message) {
        var view = new ModelAndView("error-message"); view.setStatus(status);
        view.addObject("status", status.value()); view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message); return view;
    }
    private static String redirect(Long id) { return "redirect:/staff/orders/pickup/" + id; }
}
