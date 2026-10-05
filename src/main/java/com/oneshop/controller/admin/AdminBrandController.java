package com.oneshop.controller.admin;

import com.oneshop.dto.request.BrandRequest;
import com.oneshop.dto.response.BrandResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.service.ProductService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin: Brand list with its create/edit form on one page, plus the logo (uploaded to Cloudinary; the database keeps
 * its URL and public id). Brands are hidden by status, never deleted.
 */
@Controller
@RequestMapping("/admin/brands")
public class AdminBrandController {

    private static final String VIEW = "admin/brands";

    private final ProductService productService;

    public AdminBrandController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("form", new BrandRequest());
        return page(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        BrandResponse brand = productService.getBrand(id);
        BrandRequest form = new BrandRequest();
        form.setName(brand.name());
        form.setDescription(brand.description());
        form.setStatus(brand.status());
        model.addAttribute("form", form);
        return page(model, id);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BrandRequest form, BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                BrandResponse created = productService.createBrand(form);
                // straight to the edit view, where the logo can be added
                return "redirect:/admin/brands/" + created.id() + "/edit?success=created";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        return page(model, null);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BrandRequest form,
                         BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                productService.updateBrand(id, form);
                return "redirect:/admin/brands?success=updated";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        return page(model, id);
    }

    @PostMapping("/{id}/logo")
    public String uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String outcome;
        try {
            productService.uploadBrandLogo(id, file);
            outcome = "success=logo-uploaded";
        } catch (BadRequestException ex) {
            outcome = "error=image-invalid";
        } catch (ImageStorageException ex) {
            outcome = "error=image-storage";
        }
        return "redirect:/admin/brands/" + id + "/edit?" + outcome;
    }

    @PostMapping("/{id}/logo/delete")
    public String removeLogo(@PathVariable Long id) {
        String outcome;
        try {
            productService.removeBrandLogo(id);
            outcome = "success=logo-removed";
        } catch (ImageStorageException ex) {
            outcome = "error=image-storage";
        }
        return "redirect:/admin/brands/" + id + "/edit?" + outcome;
    }

    private String page(Model model, Long editId) {
        model.addAttribute("brands", productService.getAllBrands());
        model.addAttribute("editId", editId);
        model.addAttribute("editBrand", editId == null ? null : productService.getBrand(editId));
        return VIEW;
    }
}
