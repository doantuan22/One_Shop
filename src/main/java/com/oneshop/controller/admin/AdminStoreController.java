package com.oneshop.controller.admin;

import com.oneshop.dto.request.StoreRequest;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Admin: Stores of the chain. A Store is taken out of sale with status INACTIVE, never deleted (BR-17). */
@Controller
@RequestMapping("/admin/stores")
public class AdminStoreController {

    private static final String FORM_VIEW = "admin/store-form";

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("stores", storeService.getAllStores());
        return "admin/stores";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new StoreRequest());
        model.addAttribute("editId", null);
        return FORM_VIEW;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        StoreResponse store = storeService.getStore(id);
        StoreRequest form = new StoreRequest();
        form.setCode(store.code());
        form.setName(store.name());
        form.setAddress(store.address());
        form.setProvinceCity(store.provinceCity());
        form.setArea(store.area());
        form.setPhone(store.phone());
        form.setOpeningHours(store.openingHours());
        form.setDeliveryEnabled(store.deliveryEnabled());
        form.setPickupEnabled(store.pickupEnabled());
        form.setStatus(store.status());
        model.addAttribute("form", form);
        model.addAttribute("editId", id);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StoreRequest form, BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                storeService.createStore(form);
                return "redirect:/admin/stores?success=created";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        model.addAttribute("editId", null);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") StoreRequest form,
                         BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                storeService.updateStore(id, form);
                return "redirect:/admin/stores?success=updated";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        model.addAttribute("editId", id);
        return FORM_VIEW;
    }
}
