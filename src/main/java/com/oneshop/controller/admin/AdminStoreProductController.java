package com.oneshop.controller.admin;

import com.oneshop.dto.request.StoreProductRequest;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import com.oneshop.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * Admin: StoreProduct, a SKU put on sale at one Store with that Store's price, quantity and status (BR-02). The Admin
 * sees the exact quantity. A (Store, Product) pair exists once; it is taken off sale with status INACTIVE.
 */
@Controller
@RequestMapping("/admin/store-products")
public class AdminStoreProductController {

    private static final String FORM_VIEW = "admin/store-product-form";
    private static final int PAGE_SIZE = 20;

    private final StoreProductService storeProductService;
    private final StoreService storeService;
    private final ProductService productService;

    public AdminStoreProductController(StoreProductService storeProductService, StoreService storeService,
                                       ProductService productService) {
        this.storeProductService = storeProductService;
        this.storeService = storeService;
        this.productService = productService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long storeId, @RequestParam(required = false) Long productId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("storeProducts", storeProductService.searchStoreProducts(storeId, productId,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        model.addAttribute("stores", storeService.getAllStores());
        model.addAttribute("products", productService.getAssignableProducts());
        model.addAttribute("storeId", storeId);
        model.addAttribute("productId", productId);
        return "admin/store-products";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long storeId, Model model) {
        StoreProductRequest form = new StoreProductRequest();
        form.setStoreId(storeId);
        model.addAttribute("form", form);
        return createView(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        StoreProductStockResponse current = storeProductService.getStoreProduct(id);
        StoreProductRequest form = new StoreProductRequest();
        form.setStoreId(current.storeId());
        form.setProductId(current.productId());
        form.setPrice(current.price());
        form.setQuantity(current.quantity());
        form.setStatus(current.status());
        model.addAttribute("form", form);
        return editView(model, current);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StoreProductRequest form, BindingResult bindingResult,
                         Principal principal, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                storeProductService.createStoreProduct(form, principal.getName());
                return "redirect:/admin/store-products?success=created&storeId=" + form.getStoreId();
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        return createView(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") StoreProductRequest form,
                         BindingResult bindingResult, Principal principal, Model model) {
        StoreProductStockResponse current = storeProductService.getStoreProduct(id);
        // Store and Product of an existing StoreProduct are fixed, whatever the form posts
        form.setStoreId(current.storeId());
        form.setProductId(current.productId());
        if (!bindingResult.hasFieldErrors("price") && !bindingResult.hasFieldErrors("quantity")
                && !bindingResult.hasFieldErrors("status") && !bindingResult.hasFieldErrors("note")) {
            try {
                storeProductService.updateStoreProduct(id, form, principal.getName());
                return "redirect:/admin/store-products?success=updated&storeId=" + current.storeId();
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        return editView(model, current);
    }

    private String createView(Model model) {
        model.addAttribute("stores", storeService.getAllStores());
        model.addAttribute("products", productService.getAssignableProducts());
        model.addAttribute("current", null);
        return FORM_VIEW;
    }

    private String editView(Model model, StoreProductStockResponse current) {
        model.addAttribute("current", current);
        return FORM_VIEW;
    }
}
