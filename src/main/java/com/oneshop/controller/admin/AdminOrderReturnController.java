package com.oneshop.controller.admin;

import com.oneshop.entity.OrderReturnStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.service.returns.AdminOrderReturnService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * The Administrator's queue of return requests and the two decisions. Fixed POSTs only: no route accepts a status, an
 * amount or a time. The actor is the authenticated Administrator; the Service checks the role again and applies the
 * rules under the Order lock. The answer is a redirect with a short {@code result} code.
 */
@Controller
@RequestMapping("/admin/returns")
public class AdminOrderReturnController {

    private final AdminOrderReturnService returns;

    public AdminOrderReturnController(AdminOrderReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    public String list(@RequestParam(required = false) OrderReturnStatus status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("returns", returns.list(status, page));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderReturnStatus.values());
        model.addAttribute("waiting", returns.waitingCount());
        return "admin/returns/index";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("orderReturn", returns.get(id));
        model.addAttribute("minNote", AdminOrderReturnService.MIN_NOTE);
        model.addAttribute("maxNote", AdminOrderReturnService.MAX_NOTE);
        return "admin/returns/detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Principal principal) {
        try {
            returns.approve(id, principal.getName());
            return back(id, "approved");
        } catch (FulfillmentConflictException ex) {
            return back(id, "refused:" + ex.getCode());
        } catch (BadRequestException ex) {
            return back(id, "invalid:" + ex.getField());
        }
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String note, Principal principal) {
        try {
            returns.reject(id, note, principal.getName());
            return back(id, "rejected");
        } catch (FulfillmentConflictException ex) {
            return back(id, "refused:" + ex.getCode());
        } catch (BadRequestException ex) {
            return back(id, "invalid:" + ex.getField());
        }
    }

    private static String back(Long id, String code) {
        return "redirect:/admin/returns/" + id + "?result=" + code;
    }
}
