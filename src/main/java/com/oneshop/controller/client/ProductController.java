package com.oneshop.controller.client;

import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import com.oneshop.web.SelectedStoreInterceptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client catalog and Product Detail. The Store context is the validated {@code selectedStore} request attribute set by
 * {@link SelectedStoreInterceptor}; there is no request parameter that picks a Store here.
 */
@Controller
public class ProductController {

    private static final int PAGE_SIZE = 12;

    private final ProductService productService;
    private final StoreProductService storeProductService;

    public ProductController(ProductService productService, StoreProductService storeProductService) {
        this.productService = productService;
        this.storeProductService = storeProductService;
    }

    @GetMapping("/products")
    public String list(@RequestParam(name = "q", required = false) String keyword,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long brandId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestAttribute(name = SelectedStoreInterceptor.SELECTED_STORE, required = false)
                       StoreResponse selectedStore,
                       Model model) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, categoryId, brandId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);

        model.addAttribute("catalog", selectedStore == null
                ? storeProductService.getChainCatalog(criteria, pageable)
                : storeProductService.getStoreCatalog(selectedStore.id(), criteria, pageable));
        model.addAttribute("criteria", criteria);
        model.addAttribute("categories", productService.getActiveCategories());
        model.addAttribute("brands", productService.getActiveBrands());
        return "product/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id,
                         @RequestAttribute(name = SelectedStoreInterceptor.SELECTED_STORE, required = false)
                         StoreResponse selectedStore,
                         Model model) {
        model.addAttribute("detail",
                storeProductService.getProductDetail(id, selectedStore == null ? null : selectedStore.id()));
        return "product/detail";
    }
}
