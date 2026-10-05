package com.oneshop.controller.admin;

import com.oneshop.dto.request.ProductRequest;
import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.service.ProductService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin: Products. One Product is one sellable SKU of the whole chain (BR-01): no price or quantity here, those are
 * set per Store in {@link AdminStoreProductController}. Images go to Cloudinary. A Product is retired with a status,
 * never deleted (BR-17).
 */
@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private static final String FORM_VIEW = "admin/product-form";
    private static final int PAGE_SIZE = 20;

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("products",
                productService.searchAllProducts(keyword, PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        model.addAttribute("q", keyword);
        return "admin/products";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new ProductRequest());
        return form(model, null);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AdminProductResponse product = productService.getProductForAdmin(id);
        ProductRequest form = new ProductRequest();
        form.setSku(product.sku());
        form.setName(product.name());
        form.setDescription(product.description());
        form.setCategoryId(product.categoryId());
        form.setBrandId(product.brandId());
        form.setStatus(product.status());
        model.addAttribute("form", form);
        return form(model, id);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProductRequest form, BindingResult bindingResult,
                         Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                AdminProductResponse created = productService.createProduct(form);
                // straight to the edit view, where images can be added
                return "redirect:/admin/products/" + created.id() + "/edit?success=created";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        return form(model, null);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ProductRequest form,
                         BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                productService.updateProduct(id, form);
                return "redirect:/admin/products?success=updated";
            } catch (BadRequestException ex) {
                FormErrors.reject(bindingResult, ex);
            }
        }
        return form(model, id);
    }

    // ---- images (Cloudinary) ----

    @PostMapping("/{id}/images")
    public String uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String outcome;
        try {
            productService.addProductImage(id, file);
            outcome = "success=image-uploaded";
        } catch (BadRequestException ex) {
            outcome = "error=image-invalid";
        } catch (ImageStorageException ex) {
            outcome = "error=image-storage";
        }
        return "redirect:/admin/products/" + id + "/edit?" + outcome;
    }

    @PostMapping("/{id}/images/{imageId}/primary")
    public String setPrimaryImage(@PathVariable Long id, @PathVariable Long imageId) {
        productService.setPrimaryProductImage(id, imageId);
        return "redirect:/admin/products/" + id + "/edit?success=image-primary";
    }

    @PostMapping("/{id}/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        String outcome;
        try {
            productService.deleteProductImage(id, imageId);
            outcome = "success=image-deleted";
        } catch (ImageStorageException ex) {
            outcome = "error=image-storage";
        }
        return "redirect:/admin/products/" + id + "/edit?" + outcome;
    }

    private String form(Model model, Long editId) {
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("brands", productService.getAllBrands());
        model.addAttribute("editId", editId);
        model.addAttribute("images", editId == null ? null : productService.getProductImages(editId));
        return FORM_VIEW;
    }
}
