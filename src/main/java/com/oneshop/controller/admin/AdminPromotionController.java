package com.oneshop.controller.admin;

import com.oneshop.dto.request.PromotionRequest;
import com.oneshop.dto.response.PromotionResponse;
import com.oneshop.entity.PromotionStatus;
import com.oneshop.entity.PromotionType;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.promotion.AdminPromotionService;
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

/**
 * Admin: promotion codes. List, create, edit and delete. The request never carries a use count, a status of a use or a
 * time of creation: the Service decides those. A code that was used cannot be deleted (it can be switched off).
 */
@Controller
@RequestMapping("/admin/promotions")
public class AdminPromotionController {

    private static final String LIST = "admin/promotions/index";
    private static final String FORM = "admin/promotions/form";

    private final AdminPromotionService promotions;

    public AdminPromotionController(AdminPromotionService promotions) {
        this.promotions = promotions;
    }

    @GetMapping
    public String list(@RequestParam(required = false) PromotionStatus status,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("promotions", promotions.list(status, q, page));
        model.addAttribute("status", status);
        model.addAttribute("q", q == null ? "" : q);
        return LIST;
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PromotionRequest());
        }
        return formPage(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PromotionRequest form, BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try {
                PromotionResponse created = promotions.create(form);
                return "redirect:/admin/promotions/" + created.id() + "/edit?success=created";
            } catch (BadRequestException ex) {
                FormErrors.reject(errors, ex);
            }
        }
        return formPage(model, null);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        PromotionResponse current = promotions.get(id);
        if (!model.containsAttribute("form")) {
            PromotionRequest form = new PromotionRequest();
            form.setCode(current.code());
            form.setName(current.name());
            form.setDescription(current.description());
            form.setDiscountType(current.discountType());
            form.setDiscountValue(current.discountValue().setScale(0, java.math.RoundingMode.DOWN));
            form.setMaxDiscountAmount(current.maxDiscountAmount() == null ? null : current.maxDiscountAmount().setScale(0));
            form.setMinOrderAmount(current.minOrderAmount().setScale(0));
            form.setUsageLimit(current.usageLimit());
            form.setPerUserLimit(current.perUserLimit());
            form.setStartAt(current.startAt());
            form.setEndAt(current.endAt());
            form.setStatus(current.status());
            model.addAttribute("form", form);
        }
        return formPage(model, current);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") PromotionRequest form,
                         BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try {
                promotions.update(id, form);
                return "redirect:/admin/promotions?success=updated";
            } catch (BadRequestException ex) {
                FormErrors.reject(errors, ex);
            }
        }
        return formPage(model, promotions.get(id));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        try {
            promotions.delete(id);
            return "redirect:/admin/promotions?success=deleted";
        } catch (BadRequestException ex) {
            return "redirect:/admin/promotions?error=in-use";
        }
    }

    private String formPage(Model model, PromotionResponse current) {
        model.addAttribute("current", current);
        model.addAttribute("types", PromotionType.values());
        model.addAttribute("statuses", PromotionStatus.values());
        model.addAttribute("usages", current == null ? java.util.List.of() : promotions.recentUsages(current.id()));
        return FORM;
    }
}
