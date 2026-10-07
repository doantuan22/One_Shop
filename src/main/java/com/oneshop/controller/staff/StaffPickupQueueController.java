package com.oneshop.controller.staff;

import com.oneshop.service.StaffOperationsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffPickupQueueController {
    private final StaffOperationsService operations;
    public StaffPickupQueueController(StaffOperationsService operations) { this.operations = operations; }
    @GetMapping("/staff/pickup")
    public String queue(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", operations.getPickupQueue(page));
        return "staff/pickup/queue";
    }
}
