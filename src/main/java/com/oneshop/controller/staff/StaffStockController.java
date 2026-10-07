package com.oneshop.controller.staff;

import com.oneshop.dto.request.StockAdjustRequest;
import com.oneshop.service.StaffInventoryService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/staff")
public class StaffStockController {
    private final StaffInventoryService inventory;
    public StaffStockController(StaffInventoryService inventory) { this.inventory = inventory; }

    @GetMapping("/stock")
    public String stock(@RequestParam(defaultValue = "0") int page, Model model) { return list(page, false, model); }
    @GetMapping("/inventory-history")
    public String historySelection(@RequestParam(defaultValue = "0") int page, Model model) { return list(page, true, model); }
    private String list(int page, boolean historySelection, Model model) {
        model.addAttribute("stock", inventory.getStock(page));
        model.addAttribute("historySelection", historySelection);
        model.addAttribute("listPath", historySelection ? "/staff/inventory-history" : "/staff/stock");
        return "staff/stock/index";
    }

    @GetMapping("/stock/{id}/adjust")
    public String adjustForm(@PathVariable Long id, Model model) {
        var stock = inventory.getStoreProduct(id);
        model.addAttribute("stock", stock);
        model.addAttribute("form", new StockAdjustRequest(stock.quantity(), ""));
        return "staff/stock/adjust";
    }
    @PostMapping("/stock/{id}/adjust")
    public String adjust(@PathVariable Long id, @Valid @ModelAttribute("form") StockAdjustRequest form,
                         BindingResult errors, Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            model.addAttribute("stock", inventory.getStoreProduct(id)); // Still scoped even on invalid/forged input.
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return "staff/stock/adjust";
        }
        boolean changed = inventory.adjust(id, form);
        return "redirect:/staff/inventory-history/" + id + "?success=" + (changed ? "adjusted" : "unchanged");
    }
    @GetMapping("/inventory-history/{id}")
    public String history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Model model) {
        var history = inventory.getHistory(id, page);
        model.addAttribute("stock", history.stock()); model.addAttribute("movements", history.movements());
        return "staff/stock/history";
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Bạn không được phân công điều chỉnh tồn của chi nhánh này.");
    }
    @ExceptionHandler(DataAccessException.class)
    public ModelAndView database(DataAccessException ex) {
        return error(HttpStatus.CONFLICT, "Không thể lưu điều chỉnh hoặc tồn kho đang được xử lý. Vui lòng tải lại trang và thử lại.");
    }
    private static ModelAndView error(HttpStatus status, String message) {
        var view = new ModelAndView("error-message"); view.setStatus(status);
        view.addObject("status", status.value()); view.addObject("error", status.getReasonPhrase()); view.addObject("message", message);
        return view;
    }
}
