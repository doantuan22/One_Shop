package com.oneshop.controller.admin;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService;
import com.oneshop.service.payment.OrderRefundViewService;
import com.oneshop.service.StoreService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
    public String list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) OrderStatus orderStatus,
                       @RequestParam(required = false) OrderPaymentStatus paymentStatus,
                       @RequestParam(required = false) FulfillmentType fulfillmentType,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "false") boolean attention, Model model) {
        // Validate date semantics before the attention/list split: malformed ranges are never ignored.
        com.oneshop.service.query.SqlDayRange.of(from, to);
        model.addAttribute("orders", attention ? operations.getOrdersNeedingAttention(storeId, page)
                : operations.getOrders(storeId, q, orderStatus, paymentStatus, fulfillmentType, from, to, page));
        model.addAttribute("attention", attention);
        model.addAttribute("stores", stores.getAllStores());
        model.addAttribute("storeId", storeId);
        model.addAttribute("q", q);
        model.addAttribute("orderStatus", orderStatus);
        model.addAttribute("paymentStatus", paymentStatus);
        model.addAttribute("fulfillmentType", fulfillmentType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("paymentStatuses", OrderPaymentStatus.values());
        model.addAttribute("fulfillmentTypes", FulfillmentType.values());
        return "admin/orders";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("orderDetail", operations.getOrder(id));
        model.addAttribute("exception", exceptions.getException(id));
        model.addAttribute("refund", refundView.forAdmin(id));
        return "admin/order-detail";
    }
}
