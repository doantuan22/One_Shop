package com.oneshop.controller.staff;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.service.StaffOperationsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;

/**
 * Staff Dashboard. The existing /staff landing explains missing scope without returning Store data.
 * Every actual data read is authorized independently by the service, not by layout attributes.
 */
@Controller
@RequestMapping("/staff")
public class StaffDashboardController {
    private final StaffOperationsService operations;

    public StaffDashboardController(StaffOperationsService operations) { this.operations = operations; }

    @GetMapping({"", "/"})
    public String landing(@RequestAttribute(value = "assignedStores", required = false) List<StoreResponse> assigned,
                          Model model) {
        if (assigned != null && !assigned.isEmpty()) model.addAttribute("dashboard", operations.getDashboard());
        return "staff/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("dashboard", operations.getDashboard());
        return "staff/dashboard";
    }
}
