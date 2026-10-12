package com.oneshop.controller.client;

import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.HomeBrandTile;
import com.oneshop.dto.response.HomeCategoryTile;
import com.oneshop.dto.response.HomeViewModel;
import com.oneshop.dto.response.ProductCardResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.service.CloudinaryService;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import com.oneshop.service.StoreService;
import com.oneshop.web.SelectedStoreInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Home page: only prepares the view model from the existing Services. */
@Controller
public class HomeController {

    static final int PRODUCTS = 8;
    static final int STORES = 4;
    static final int BRANDS = 12;
    static final int SEARCH_SUGGESTIONS = 3;
    private static final int CATEGORY_IMAGE_WIDTH = 800;

    private final ProductService productService;
    private final StoreService storeService;
    private final StoreProductService storeProductService;
    private final Clock clock;

    public HomeController(ProductService productService, StoreService storeService,
                          StoreProductService storeProductService, ObjectProvider<Clock> clock) {
        this.productService = productService;
        this.storeService = storeService;
        this.storeProductService = storeProductService;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
    }

    @GetMapping("/")
    public String home(@RequestAttribute(name = SelectedStoreInterceptor.SELECTED_STORE, required = false)
                       StoreResponse selectedStore, Model model) {
        List<HomeCategoryTile> categories = productService.getActiveCategories().stream()
                .map(HomeController::tile).toList();

        Long storeId = selectedStore == null ? null : selectedStore.id();
        List<ProductCardResponse> products = storeProductService
                .getRandomSellable(storeId, PRODUCTS, seed(storeId)).stream()
                .map(ProductCardResponse::from).toList();

        Map<Long, Long> counts = productService.countVisibleProductsByBrand();
        List<HomeBrandTile> brands = productService.getActiveBrands().stream()
                .filter(brand -> counts.getOrDefault(brand.id(), 0L) > 0)
                .map(brand -> brandTile(brand, counts)).limit(BRANDS).toList();

        List<StoreResponse> stores = storeService.getActiveStores().stream().limit(STORES).toList();

        model.addAttribute("home", new HomeViewModel(categories, layoutFor(categories.size()), products, brands,
                categories.stream().limit(SEARCH_SUGGESTIONS).toList(),
                brands.stream().limit(SEARCH_SUGGESTIONS).toList(), stores));
        return "home/index";
    }

    /**
     * Same hour (and same Store) gives the same seed, so refreshing the page keeps the same products; the pick changes
     * when the hour or the selected Store changes.
     */
    long seed(Long storeId) {
        long hour = clock.instant().truncatedTo(ChronoUnit.HOURS).getEpochSecond() / 3600;
        return hour * 31 + (storeId == null ? 0 : storeId);
    }

    /** Layout of the category tiles by how many categories there are (not by their names). */
    static String layoutFor(int categories) {
        return switch (categories) {
            case 0 -> "none";
            case 1 -> "single";
            case 2 -> "pair";
            case 3 -> "bento";
            default -> "grid";
        };
    }

    private static HomeCategoryTile tile(CategoryResponse category) {
        String url = category.imageUrl();
        if (url != null && url.isBlank()) {
            url = null;
        }
        String optimizedUrl = CloudinaryService.transformUrl(url, CATEGORY_IMAGE_WIDTH);
        return new HomeCategoryTile(category.id(), category.name(), optimizedUrl);
    }

    private static HomeBrandTile brandTile(BrandResponse brand, Map<Long, Long> counts) {
        return new HomeBrandTile(brand.id(), brand.name(), counts.getOrDefault(brand.id(), 0L));
    }
}
