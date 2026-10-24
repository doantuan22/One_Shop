package com.oneshop.controller.staff;

import com.oneshop.dto.request.StockAdjustRequest;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.service.StaffInventoryService;
import com.oneshop.service.StaffOperationsService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;

/**
 * Staff stock console ({@code /staff/stock}), the central movement log ({@code /staff/inventory-history}) and the
 * per-product movement history ({@code /staff/inventory-history/{id}}). The adjust POST keeps the audited note flow
 * and redirects to the product history so the recorded movement is immediately visible.
 */
@Controller
@RequestMapping("/staff")
public class StaffStockController {
    private final StaffInventoryService inventory;
    public StaffStockController(StaffInventoryService inventory) { this.inventory = inventory; }

    @GetMapping("/stock")
    public String stock(@RequestParam(required = false) String q,
                        @RequestParam(name = "level", required = false) String level,
                        @RequestParam(required = false) String sort,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("stock", inventory.getStock(page, q, level, sort));
        model.addAttribute("stats", inventory.stockStats());
        model.addAttribute("historySelection", false);
        model.addAttribute("lowStockThreshold", StaffOperationsService.LOW_STOCK_THRESHOLD);
        model.addAttribute("listPath", "/staff/stock");
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("level", level == null ? "all" : level);
        model.addAttribute("sort", sort == null ? "" : sort);
        return "staff/stock/index";
    }

    @GetMapping("/inventory-history")
    public String historySelection(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) InventoryMovementType type,
                                   @RequestParam(required = false) Long staffId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(name = "view", required = false) String view,
                                   @RequestParam(name = "logPage", defaultValue = "0") int logPage,
                                   @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("stock", inventory.getStock(page));
        model.addAttribute("log", inventory.getMovementLog(logPage, q, type, staffId, from, to));
        model.addAttribute("staffOptions", inventory.staffOptions());
        model.addAttribute("historySelection", true);
        model.addAttribute("viewIsSelect", "select".equals(view));
        model.addAttribute("lowStockThreshold", StaffOperationsService.LOW_STOCK_THRESHOLD);
        model.addAttribute("listPath", "/staff/inventory-history");
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("type", type == null ? "" : type.name());
        model.addAttribute("staffId", staffId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "staff/stock/inventory-history";
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
