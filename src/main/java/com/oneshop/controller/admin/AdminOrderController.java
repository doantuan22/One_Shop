package com.oneshop.controller.admin;

import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService;
import com.oneshop.service.payment.OrderRefundViewService;
import com.oneshop.service.StoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {
    private final AdminOperationsService operations;
    private final StoreService stores;
    private final AdminFulfillmentExceptionService exceptions;
    private final OrderRefundViewService refundView;
    public AdminOrderController(AdminOperationsService operations, StoreService stores,
                                AdminFulfillmentExceptionService exceptions, OrderRefundViewService refundView) {
        this.operations = operations; this.stores = stores; this.exceptions = exceptions; this.refundView = refundView;
    }
    @GetMapping
    public String list(@RequestParam(required = false) Long storeId, @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false, defaultValue = "false") boolean attention, Model model) {
        model.addAttribute("orders", attention ? operations.getOrdersNeedingAttention(storeId, page)
                : operations.getOrders(storeId, page));
        model.addAttribute("attention", attention);
        model.addAttribute("stores", stores.getAllStores()); model.addAttribute("storeId", storeId); return "admin/orders";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("orderDetail", operations.getOrder(id));
        model.addAttribute("exception", exceptions.getException(id));
        model.addAttribute("refund", refundView.forAdmin(id));
        return "admin/order-detail";
    }
}
