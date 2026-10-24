package com.oneshop.controller.staff;

import com.oneshop.entity.ReturnDisposition;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.service.returns.StaffOrderReturnService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * Staff work list of returned goods ({@code /staff/returns}) and the choice for each line: put it back on the shelf
 * or handle it outside the system. Two fixed POSTs; the Store of a line is never taken from the request, it is the
 * Store that delivered the line and is checked against the Staff's assignments.
 */
@Controller
@RequestMapping("/staff/returns")
public class StaffOrderReturnController {

    private final StaffOrderReturnService returns;

    public StaffOrderReturnController(StaffOrderReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "pending") String view,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        boolean all = "all".equals(view);
        model.addAttribute("lines", returns.list(all ? null : ReturnDisposition.PENDING, page));
        model.addAttribute("view", all ? "all" : "pending");
        model.addAttribute("waiting", returns.waitingCount());
        model.addAttribute("maxNote", StaffOrderReturnService.MAX_NOTE);
        return "staff/returns/index";
    }

    @PostMapping("/items/{itemId}/restock")
    public String restock(@PathVariable Long itemId, @RequestParam(required = false) String note, Principal principal) {
        return handle(itemId, ReturnDisposition.RESTOCKED, note, principal);
    }

    @PostMapping("/items/{itemId}/dispose")
    public String dispose(@PathVariable Long itemId, @RequestParam(required = false) String note, Principal principal) {
        return handle(itemId, ReturnDisposition.DISPOSED, note, principal);
    }

    private String handle(Long itemId, ReturnDisposition target, String note, Principal principal) {
        try {
            returns.handle(itemId, target, note, principal.getName());
            return back(target == ReturnDisposition.RESTOCKED ? "restocked" : "disposed");
        } catch (AccessDeniedException ex) {
            return back("forbidden");
        } catch (FulfillmentConflictException ex) {
            return back("refused:" + ex.getCode());
        } catch (BadRequestException ex) {
            return back("invalid:" + ex.getField());
        }
    }

    private static String back(String code) {
        return "redirect:/staff/returns?result=" + code;
    }
}
