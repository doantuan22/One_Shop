package com.oneshop.controller.client;

import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.service.ProductService;
import com.oneshop.service.ReviewService;
import com.oneshop.service.StoreProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

/**
 * Client catalog and Product Detail. V3 sells one shared catalog with one online price per SKU: browsing and buying
 * never depend on a chosen Store, so the catalog is always the whole chain. Stores only appear as an optional
 * availability lookup on the Product Detail.
 */
@Controller
public class ProductController {

    private static final int PAGE_SIZE = 12;

    private final ProductService productService;
    private final StoreProductService storeProductService;
    private final ReviewService reviewService;

    public ProductController(ProductService productService, StoreProductService storeProductService,
                             ReviewService reviewService) {
        this.productService = productService;
        this.storeProductService = storeProductService;
        this.reviewService = reviewService;
    }

    /** Product names and SKUs for the search-box suggestions (the public catalog; no stock or price). */
    @GetMapping("/products/suggest")
    @ResponseBody
    public java.util.List<java.util.Map<String, String>> suggest() {
        return productService.searchProducts(ProductSearchCriteria.none(), com.oneshop.service.query.Paging.page(0, 500)).getContent().stream()
                .map(p -> java.util.Map.of("sku", p.sku(), "name", p.name()))
                .toList();
    }

    @GetMapping("/products")
    public String list(@RequestParam(name = "q", required = false) String keyword,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long brandId,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, categoryId, brandId);
        Pageable pageable = com.oneshop.service.query.Paging.page(page, PAGE_SIZE);

        model.addAttribute("catalog", storeProductService.getChainCatalog(criteria, pageable));
        model.addAttribute("criteria", criteria);
        model.addAttribute("categories", productService.getActiveCategories());
        model.addAttribute("brands", productService.getActiveBrands());
        return "product/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id,
                         Principal principal, Model model) {
        model.addAttribute("detail", storeProductService.getProductDetail(id, null));
        model.addAttribute("productReviews", reviewService.getProductReviews(id));
        model.addAttribute("reviewItems", reviewService.getProductReviewItems(principal == null ? null : principal.getName(), id));
        return "product/detail";
    }
}
