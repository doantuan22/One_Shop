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
import org.springframework.web.bind.annotation.RequestParam;

/** Admin: Stores of the chain. A Store is taken out of sale with status INACTIVE, never deleted (BR-17). */
@Controller
@RequestMapping("/admin/stores")
public class AdminStoreController {

    private static final String FORM_VIEW = "admin/store-form";

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** Store code and name for the search-box suggestions (os-suggest.js reads "sku" as the short code). */
    @GetMapping("/suggest")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.List<java.util.Map<String, String>> suggest() {
        return storeService.getAllStores().stream()
                .map(s -> java.util.Map.of("sku", s.code(), "name", s.name()))
                .toList();
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String area,
                       @RequestParam(required = false) com.oneshop.entity.ActiveStatus status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        var all = storeService.getAllStores();
        var results = storeService.searchStores(q, area, status, page);
        // pagination page object is always materialized even when the service layer yields nothing
        model.addAttribute("stores", results == null ? org.springframework.data.domain.Page.empty() : results);
        // area options come from the live data, no hard-coded list
        model.addAttribute("areas", all == null ? java.util.List.of()
                : all.stream().map(s -> s.area()).distinct().sorted().toList());
        model.addAttribute("statuses", com.oneshop.entity.ActiveStatus.values());
        model.addAttribute("q", q);
        model.addAttribute("area", area);
        model.addAttribute("status", status);
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
