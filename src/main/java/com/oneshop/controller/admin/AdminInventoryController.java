package com.oneshop.controller.admin;

import com.oneshop.service.StoreProductService;
import com.oneshop.service.StoreService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only inventory console of the whole chain (admin/inventory). It is deliberately separate from the
 * StoreProduct assignment page (/admin/store-products): this screen watches quantities chain-wide, while
 * that screen manages which SKU is on sale at which Store. Every KPI here is a live count, never a constant.
 */
@Controller
public class AdminInventoryController {
    private final StoreProductService stock;
    private final StoreService stores;
    public AdminInventoryController(StoreProductService stock, StoreService stores) {
        this.stock = stock; this.stores = stores;
    }
    @GetMapping("/admin/inventory")
    public String list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String stockLevel,
                       @RequestParam(required = false) String sort,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("storeProducts", stock.searchInventory(storeId, q, stockLevel, sort,
                PageRequest.of(Math.max(page, 0), 20)));
        model.addAttribute("summary", stock.inventorySummary(storeId));
        model.addAttribute("stores", stores.getAllStores());
        model.addAttribute("storeId", storeId);
        model.addAttribute("q", q);
        model.addAttribute("stockLevel", stockLevel);
        model.addAttribute("sort", sort);
        return "admin/inventory";
    }
}
