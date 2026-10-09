package com.oneshop.controller.admin;

import com.oneshop.service.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Reuses the existing Phase 6 StoreProduct queries/DTO/template, no separate stock writer. */
@Controller
public class AdminInventoryController {
    private final StoreProductService stock;
    private final StoreService stores;
    private final ProductService products;
    public AdminInventoryController(StoreProductService stock, StoreService stores, ProductService products) {
        this.stock = stock; this.stores = stores; this.products = products;
    }
    @GetMapping("/admin/inventory")
    public String list(@RequestParam(required = false) Long storeId, @RequestParam(required = false) Long productId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("storeProducts", stock.searchStoreProducts(storeId, productId, PageRequest.of(Math.max(0, page), 20)));
        model.addAttribute("stores", stores.getAllStores()); model.addAttribute("products", products.getAssignableProducts());
        model.addAttribute("storeId", storeId); model.addAttribute("productId", productId); model.addAttribute("inventoryView", true);
        return "admin/store-products";
    }
}
