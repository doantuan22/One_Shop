package com.oneshop.controller.admin;

import com.oneshop.dto.request.CategoryRequest;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.exception.BadRequestException;
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

import com.oneshop.exception.ImageStorageException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Admin: Category list with its create/edit form on one page. Categories are hidden by status, never deleted. */
@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private static final String VIEW = "admin/categories";

    private final ProductService productService;

    public AdminCategoryController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CategoryRequest());
        }
        return page(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("form")) {
            CategoryResponse category = productService.getCategory(id);
            CategoryRequest form = new CategoryRequest();
            form.setName(category.name());
            form.setDescription(category.description());
            form.setStatus(category.status());
            model.addAttribute("form", form);
        }
        return page(model, id);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CategoryRequest form, BindingResult bindingResult,
                         @RequestParam(name = "file", required = false) MultipartFile file,
                         Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                productService.createCategory(form, file);
                return "redirect:/admin/categories?success=created";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            } catch (ImageStorageException ex) {
                bindingResult.reject("imageStorage", "Không thể tải ảnh. Vui lòng thử lại hoặc liên hệ quản trị viên.");
            }
        }
        return page(model, null);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") CategoryRequest form,
                         BindingResult bindingResult,
                         @RequestParam(name = "file", required = false) MultipartFile file,
                         Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                productService.updateCategory(id, form, file);
                return "redirect:/admin/categories?success=updated";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            } catch (ImageStorageException ex) {
                bindingResult.reject("imageStorage", "Không thể tải ảnh. Vui lòng thử lại hoặc liên hệ quản trị viên.");
            }
        }
        return page(model, id);
    }

    @PostMapping("/{id}/image")
    public String uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String outcome;
        try {
            productService.uploadCategoryImage(id, file);
            outcome = "success=image-uploaded";
        } catch (BadRequestException ex) {
            outcome = ex.getMessage() != null && ex.getMessage().contains("5MB") ? "error=image-size" : "error=image-invalid";
        } catch (ImageStorageException ex) {
            outcome = "error=image-storage";
        }
        return "redirect:/admin/categories/" + id + "/edit?" + outcome;
    }

    @PostMapping("/{id}/image/delete")
    public String removeImage(@PathVariable Long id) {
        String outcome;
        try {
            productService.removeCategoryImage(id);
            outcome = "success=image-removed";
        } catch (ImageStorageException ex) {
            outcome = "error=image-storage";
        }
        return "redirect:/admin/categories/" + id + "/edit?" + outcome;
    }

    private String page(Model model, Long editId) {
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("editId", editId);
        model.addAttribute("editCategory", editId == null ? null : productService.getCategory(editId));
        return VIEW;
    }
}
