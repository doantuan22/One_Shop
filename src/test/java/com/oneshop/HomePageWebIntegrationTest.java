package com.oneshop;

import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The home page ("/") on the web layer: services are mocked, no database. */
class HomePageWebIntegrationTest extends AbstractIntegrationTest {

    private static final String STORE_COOKIE = "ONESHOP_STORE";

    private static final StoreResponse GO_VAP = new StoreResponse(2L, "OS-GOVAP", "OneShop Gò Vấp", "202 Quang Trung",
            "TP. Hồ Chí Minh", "Gò Vấp", "02811110002", "08:00 - 22:00", true, false, ActiveStatus.ACTIVE);
    private static final StoreResponse THU_DUC = new StoreResponse(1L, "OS-THUDUC", "OneShop Thủ Đức", "101 Võ Văn Ngân",
            "TP. Hồ Chí Minh", "Thủ Đức", "02811110001", "08:00 - 22:00", true, true, ActiveStatus.ACTIVE);
    private static final ProductResponse SERUM = new ProductResponse(3L, "SIMPLE-PURIFY-150", "Cocoon Serum Bí Đao 30ml",
            "Mô tả", "Chăm sóc da", "Cocoon", "https://res.cloudinary.com/demo/serum.jpg",
            new BigDecimal("138000.00"), new BigDecimal("159000.00"));
    private static final ProductResponse TONER = new ProductResponse(4L, "CERAVE-HYDRA-236", "Innisfree Toner", "Mô tả",
            "Chăm sóc da", "Innisfree", null);
    private static final StoreAvailabilityResponse SERUM_GO_VAP =
            new StoreAvailabilityResponse(2L, 2L, "OneShop Gò Vấp", new BigDecimal("135000.00"), true);
    private static final StoreAvailabilityResponse SERUM_THU_DUC =
            new StoreAvailabilityResponse(1L, 1L, "OneShop Thủ Đức", new BigDecimal("129000.00"), true);
    private static final StoreAvailabilityResponse TONER_QUAN_7 =
            new StoreAvailabilityResponse(13L, 3L, "OneShop Quận 7", new BigDecimal("259000.00"), false);

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private static List<CategoryResponse> categories(int count) {
        List<CategoryResponse> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String img = i == 1 ? "/images/site/categories/category-skincare.webp" : null;
            list.add(new CategoryResponse((long) i, i == 1 ? "Chăm sóc da" : "Danh mục " + i, "", img, VisibilityStatus.ACTIVE));
        }
        return list;
    }

    @BeforeEach
    void stubs() {
        when(storeService.findSelectableStore(2L)).thenReturn(Optional.of(GO_VAP));
        when(productService.getActiveCategories()).thenReturn(categories(3));
        when(productService.getActiveBrands()).thenReturn(List.of(
                new BrandResponse(2L, "Cocoon", "", null, VisibilityStatus.ACTIVE),
                new BrandResponse(3L, "Innisfree", "", null, VisibilityStatus.ACTIVE),
                new BrandResponse(4L, "Rỗng", "", null, VisibilityStatus.ACTIVE)));
        when(productService.countVisibleProductsByBrand()).thenReturn(Map.of(2L, 12L, 3L, 1L));
        when(storeService.getActiveStores()).thenReturn(List.of(THU_DUC, GO_VAP));
        when(storeProductService.getRandomSellable(isNull(), eq(8), anyLong())).thenReturn(List.of(
                new CatalogItemResponse(SERUM, null, List.of(SERUM_GO_VAP, SERUM_THU_DUC)),
                new CatalogItemResponse(TONER, null, List.of(TONER_QUAN_7))));
        when(storeProductService.getRandomSellable(eq(2L), eq(8), anyLong())).thenReturn(List.of(
                new CatalogItemResponse(SERUM, SERUM_GO_VAP, List.of())));
    }

    private String customerCookie() {
        String email = "customer@oneshop.test";
        User user = new User();
        user.setEmail(email);
        user.setFullName("Khách");
        user.setPasswordHash("x");
        user.setRole(new Role(RoleName.CUSTOMER));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        withId(user);
        return "ONESHOP_TOKEN=" + jwtService.generateToken(userDetailsService.loadUserByUsername(email));
    }

    // ------------------------------------------------------------------ search

    @Test
    void searchBarIsTheOnlyMainActionAndPostsToTheCatalogSearch() throws Exception {
        String html = get("/").body();

        assertThat(html).contains("role=\"search\"", "action=\"/products\"", "name=\"q\"", "type=\"search\"",
                "enterkeyhint=\"search\"", "autocomplete=\"off\"", "aria-label=\"Tìm sản phẩm\"",
                "Tìm serum, son, sữa rửa mặt…");
        assertThat(html).doesNotContain("mã SKU", "home-hero__actions", "Chào mừng đến với", "Xem sản phẩm</a>");
        assertThat(html).contains("fetchpriority=\"high\"", "hero-mobile-480.webp", "hero-1920.webp");
    }

    @Test
    void suggestionChipsAreRealCategoriesAndBrandsAndThereIsNoStoreScopeLine() throws Exception {
        String chain = get("/").body();

        assertThat(chain).contains("href=\"/products?categoryId=1\"", "href=\"/products?categoryId=3\"",
                "href=\"/products?brandId=2\"", "href=\"/products?brandId=3\"");
        // a brand with no product is not suggested
        assertThat(chain).doesNotContain("brandId=4");
        // V3: the search is never scoped to a Store, not even with a stale Store cookie
        assertThat(chain).doesNotContain("Đang tìm trong:", "Toàn chuỗi");
        String store = get("/", "Cookie", STORE_COOKIE + "=2").body();
        assertThat(store).doesNotContain("Đang tìm trong:", "OneShop Gò Vấp</strong>");
    }

    @Test
    void chipRowIsHiddenWithoutCategoriesAndBrands() throws Exception {
        when(productService.getActiveCategories()).thenReturn(List.of());
        when(productService.getActiveBrands()).thenReturn(List.of());

        String html = get("/").body();

        assertThat(html).doesNotContain("home-search__chips", "home-chip\"").contains("role=\"search\"");
    }

    @Test
    void atMostThreeCategoryAndThreeBrandChips() throws Exception {
        when(productService.getActiveCategories()).thenReturn(categories(6));
        List<BrandResponse> brands = new ArrayList<>();
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (long i = 1; i <= 7; i++) {
            brands.add(new BrandResponse(i, "Brand " + i, "", null, VisibilityStatus.ACTIVE));
            counts.put(i, i);
        }
        when(productService.getActiveBrands()).thenReturn(brands);
        when(productService.countVisibleProductsByBrand()).thenReturn(counts);

        String html = get("/").body();

        assertThat(count(html, "class=\"home-chip\"")).isEqualTo(6);
    }

    // ------------------------------------------------------------------ products

    @Test
    void cardsAreOneLinkWithTheSharedPriceAndAChainWideStockFlag() throws Exception {
        String html = get("/").body();

        assertThat(html).contains("Khám phá sản phẩm", "Cocoon Serum Bí Đao 30ml", "Còn hàng toàn hệ thống",
                "Hết hàng", "stretched-link", "href=\"/products\"");
        assertThat(html).doesNotContain("data-store-scope", "Còn hàng ở 2 chi nhánh", "Chọn chi nhánh",
                "Sản phẩm mới", "Xem chi tiết");
        // a guest cannot add to the cart from a card: login instead
        assertThat(html).doesNotContain("action=\"/cart/items\"").contains("Đăng nhập để mua");
        assertThat(count(html, "data-reveal")).isEqualTo(2);
        verify(storeProductService, atLeastOnce()).getRandomSellable(isNull(), eq(8), anyLong());
    }

    @Test
    void aCustomerAddsFromACardByProductIdWithoutAnyStore() throws Exception {
        // even with a stale Store cookie the home is the whole chain and the form carries the product, never a Store
        String html = get("/", "Cookie", STORE_COOKIE + "=2; " + customerCookie()).body();

        assertThat(html).contains("action=\"/cart/items\"", "name=\"productId\" value=\"3\"",
                "name=\"quantity\" value=\"1\"", "Thêm vào giỏ");
        assertThat(html).doesNotContain("name=\"storeProductId\"", "data-store-scope");
        Matcher form = Pattern.compile("action=\"/cart/items\".*?</form>", Pattern.DOTALL).matcher(html);
        assertThat(form.find()).isTrue();
        assertThat(form.group()).contains("name=\"_csrf\"");
        verify(storeProductService, never()).getRandomSellable(eq(2L), eq(8), anyLong());
    }

    @Test
    void theSeedIsStableWithinTheHour() throws Exception {
        get("/");
        get("/");

        ArgumentCaptor<Long> chainSeed = ArgumentCaptor.forClass(Long.class);
        verify(storeProductService, atLeastOnce()).getRandomSellable(isNull(), eq(8), chainSeed.capture());
        assertThat(chainSeed.getAllValues()).as("same hour, same seed: a refresh keeps the products").hasSizeGreaterThan(1);
        assertThat(chainSeed.getAllValues().get(0)).isEqualTo(chainSeed.getAllValues().get(1));
    }

    @Test
    void aGuestIsSentToLoginInsteadOfGettingAnAddToCartForm() throws Exception {
        String html = get("/", "Cookie", STORE_COOKIE + "=2").body();

        assertThat(html).contains("Đăng nhập để mua").doesNotContain("action=\"/cart/items\"");
    }

    @Test
    void productsSectionIsHiddenWithoutProducts() throws Exception {
        when(storeProductService.getRandomSellable(isNull(), eq(8), anyLong())).thenReturn(List.of());

        String html = get("/").body();

        assertThat(html).contains("home-hero", "home-cta").doesNotContain("id=\"home-products\"", "Khám phá sản phẩm");
    }

    @Test
    void noStockQuantityIsPrintedAnywhere() throws Exception {
        for (String cookie : List.of("", STORE_COOKIE + "=2")) {
            String html = cookie.isEmpty() ? get("/").body() : get("/", "Cookie", cookie).body();

            assertThat(html).doesNotContainIgnoringCase("tồn kho").doesNotContain("data-quantity", "data-stock",
                    "available-quantity");
            Matcher quantities = Pattern.compile("name=\"quantity\" value=\"(\\d+)\"").matcher(html);
            while (quantities.find()) {
                assertThat(quantities.group(1)).isEqualTo("1");
            }
        }
    }

    // ------------------------------------------------------------------ categories and brands

    @Test
    void categoryLayoutFollowsTheNumberOfCategories() throws Exception {
        Map<Integer, String> expected = Map.of(1, "home-cats--single", 2, "home-cats--pair", 3, "home-cats--bento",
                5, "home-cats--grid");
        for (Map.Entry<Integer, String> entry : expected.entrySet()) {
            when(productService.getActiveCategories()).thenReturn(categories(entry.getKey()));

            String html = get("/").body();

            assertThat(html).as(entry.getKey() + " categories").contains(entry.getValue());
            assertThat(count(html, "<a class=\"home-tile")).isEqualTo(entry.getKey());
            assertThat(html).contains("href=\"/products?categoryId=1\"");
        }
        when(productService.getActiveCategories()).thenReturn(List.of());
        assertThat(get("/").body()).doesNotContain("id=\"home-categories\"");
    }

    @Test
    void categoryTilesUseTheBundledPhotoOrANeutralTile() throws Exception {
        String html = get("/").body();

        // "Chăm sóc da" has a photo; "Danh mục 2" and "Danh mục 3" have none and stay readable tiles
        assertThat(html).contains("/images/site/categories/category-skincare.webp", "Xem sản phẩm &rarr;");
        assertThat(count(html, "home-tile--plain")).isEqualTo(2);
        assertThat(html).containsPattern("home-tile--plain[^>]*>\\s*<span class=\"home-tile__body\">\\s*<span class=\"home-tile__name\">Danh mục 2");
    }

    @Test
    void categoryTileTransformsCloudinaryUrlAndRendersDynamically() throws Exception {
        when(productService.getActiveCategories()).thenReturn(List.of(
                new CategoryResponse(99L, "Danh mục mới", "", "https://res.cloudinary.com/demo/image/upload/v1234/cat.jpg", VisibilityStatus.ACTIVE)
        ));

        String html = get("/").body();

        assertThat(html).contains("https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,w_800/v1234/cat.jpg");
        assertThat(html).contains("Danh mục mới");
        assertThat(html).doesNotContain("home-tile--plain");
    }

    @Test
    void brandTilesAreTextOnlyLinksWithProductCounts() throws Exception {
        String html = get("/").body();

        assertThat(html).contains("href=\"/products?brandId=2\"", "href=\"/products?brandId=3\"", "12 sản phẩm",
                "1 sản phẩm").doesNotContain("brandId=4", "home-brands--scroll");
        assertThat(html).doesNotContainPattern("<a class=\"home-brand\"[^>]*>[^<]*<img");
    }

    @Test
    void moreThanSixBrandsBecomeAScrollingRow() throws Exception {
        List<BrandResponse> brands = new ArrayList<>();
        Map<Long, Long> counts = new java.util.HashMap<>();
        IntStream.rangeClosed(1, 8).forEach(i -> {
            brands.add(new BrandResponse((long) i, "Brand " + i, "", null, VisibilityStatus.ACTIVE));
            counts.put((long) i, 3L);
        });
        when(productService.getActiveBrands()).thenReturn(brands);
        when(productService.countVisibleProductsByBrand()).thenReturn(counts);

        assertThat(get("/").body()).contains("home-brands--scroll");
    }

    @Test
    void sectionsWithoutDataAreHidden() throws Exception {
        when(productService.getActiveCategories()).thenReturn(List.of());
        when(productService.getActiveBrands()).thenReturn(List.of());
        when(storeService.getActiveStores()).thenReturn(List.of());
        when(storeProductService.getRandomSellable(isNull(), eq(8), anyLong())).thenReturn(List.of());

        String html = get("/").body();

        assertThat(html).contains("home-hero", "home-cta")
                .doesNotContain("id=\"home-categories\"", "id=\"home-products\"", "id=\"home-brands\"",
                        "id=\"home-stores\"");
    }

    @Test
    void storesSectionIsALookupWithoutAnyStorePicker() throws Exception {
        String html = get("/").body();

        assertThat(html).contains("OneShop Thủ Đức", "Giao tận nơi", "Nhận tại cửa hàng", "Xem tất cả cửa hàng");
        assertThat(html).doesNotContain("action=\"/stores/select\"", "Chọn chi nhánh", "Đang chọn chi nhánh này");
    }

    // ------------------------------------------------------------------ page-wide

    @Test
    void everyImageHasAltWidthAndHeight() throws Exception {
        String html = get("/", "Cookie", STORE_COOKIE + "=2").body();

        Matcher images = Pattern.compile("<img\\b[^>]*>", Pattern.DOTALL).matcher(html);
        int checked = 0;
        while (images.find()) {
            String tag = images.group();
            if (tag.contains("navbar-logo-img") || tag.contains("footer-logo-img")) {
                continue; // shared navbar/footer, outside this page
            }
            checked++;
            assertThat(tag).as(tag).containsPattern("\\balt=\"").containsPattern("\\bwidth=\"\\d+\"")
                    .containsPattern("\\bheight=\"\\d+\"");
        }
        assertThat(checked).isGreaterThanOrEqualTo(4);
    }

    @Test
    void imagesBelowTheFoldAreLazyAndOnlyTheHeroIsEager() throws Exception {
        String html = get("/").body();

        Matcher images = Pattern.compile("<img\\b[^>]*>", Pattern.DOTALL).matcher(html);
        while (images.find()) {
            String tag = images.group();
            if (tag.contains("home-tile__img") || tag.contains("home-cta__img")) {
                assertThat(tag).contains("loading=\"lazy\"");
            }
            if (tag.contains("home-hero__img")) {
                assertThat(tag).doesNotContain("loading=\"lazy\"");
            }
        }
    }

    @Test
    void headerAndFooterNoLongerHardCodeTheHotline() throws Exception {
        String html = get("/").body();

        assertThat(html).doesNotContain("Hotline", "1900 0000", "Miễn phí vận chuyển");
        // V3: the header no longer asks for a Store; it only points to the Store lookup
        assertThat(html).doesNotContain("Chi nhánh đang chọn", "id=\"store-context\"");
        assertThat(html).contains("id=\"site-topbar\"", "href=\"/stores\"", "hotro@oneshop.vn");
    }

    @Test
    void theRevealScriptIsLoadedAsAFileAndPageStylesAreNotInline() throws Exception {
        String html = get("/").body();

        assertThat(html).contains("/js/home-reveal.js", "/css/home.css");
        assertThat(html).doesNotContain("https://cdn.");
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
