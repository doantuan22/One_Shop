package com.oneshop.controller.admin;

import com.oneshop.service.AdminRevenueService;
import com.oneshop.service.AdminRevenueService.GroupBy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

/** Revenue report of the chain (collected Orders, shipping fee excluded), by period and by group. */
@Controller
@RequestMapping("/admin/revenue")
public class AdminRevenueController {

    private final AdminRevenueService revenue;
    private final Clock clock;

    public AdminRevenueController(AdminRevenueService revenue, Clock clock) {
        this.revenue = revenue;
        this.clock = clock;
    }

    /** Default period: from the first day of this month to today. An unknown group falls back to the day. */
    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) String groupBy, Model model) {
        var today = LocalDate.now(clock);
        var start = from != null ? from : today.withDayOfMonth(1);
        var end = to != null ? to : today;
        GroupBy group = GroupBy.DAY;
        if (groupBy != null) {
            try {
                group = GroupBy.valueOf(groupBy);
            } catch (IllegalArgumentException ignored) {
                // keep the default
            }
        }
        model.addAttribute("report", revenue.report(start, end, group));
        model.addAttribute("groups", GroupBy.values());
        return "admin/revenue";
    }
}
