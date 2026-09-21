package com.oneshop.controller.web;

import com.oneshop.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductWebController {

    private static final int PAGE_SIZE = 12;

    private final ProductService productService;

    public ProductWebController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("createdAt").descending());
        model.addAttribute("products", productService.getActiveProducts(pageable));
        return "product/list";
    }
}
