package com.oneshop;

import com.oneshop.config.CloudinaryProperties;
import com.oneshop.entity.CartItem;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Phase 6 end to end: real Tomcat (SiteMesh, security filters, cookies, CSRF) on the real SQL Server with its seed
 * data. Requests are made the way a browser makes them.
 *
 * <p>Skipped without a configured database. Nothing here changes the database: the pages are read, and the only
 * writes attempted are ones that must be refused (wrong role, missing CSRF token, invalid data).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.jwt.secret=test-only-secret-test-only-secret-1234567890","app.cloudinary.cloud-name=","app.cloudinary.api-key=","app.cloudinary.api-secret="})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class CatalogHttpDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String JWT_COOKIE = "ONESHOP_TOKEN";
    private static final String STORE_COOKIE = "ONESHOP_STORE";
    private static final String FORM = "application/x-www-form-urlencoded";

    @LocalServerPort
    private int port;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private CloudinaryProperties cloudinaryProperties;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> tokens = new HashMap<>();

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String contentType, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", contentType.startsWith("application/json") ? "application/json" : "text/html")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String storeCookie(Object value) {
        return STORE_COOKIE + "=" + value;
    }

    private Long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    private Long productId(String sku) {
        return productRepository.findBySku(sku).orElseThrow().getId();
    }

    /** JWT cookie of a seed account, obtained by a real login. */
    private String loginCookie(String email) throws IOException, InterruptedException {
        if (!tokens.containsKey(email)) {
            HttpResponse<String> login = post("/api/auth/login", "application/json",
                    "{\"email\":\"" + email + "\",\"password\":\"" + SEED_PASSWORD + "\"}");
            Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.body());
            assertThat(token.find()).as("login of " + email).isTrue();
            tokens.put(email, token.group(1));
        }
        return JWT_COOKIE + "=" + tokens.get(email);
    }

    private String admin() throws IOException, InterruptedException {
        return loginCookie("admin@oneshop.vn");
    }

    private String customer() throws IOException, InterruptedException {
        return loginCookie("khachhang1@example.com");
    }

    private String staff() throws IOException, InterruptedException {
        return loginCookie("staff.thuduc@oneshop.vn");
    }

    /** CSRF token of a page: the hidden form field and the cookie that must come back with it. */
    private record Csrf(String field, String cookie) {
    }

    private Csrf csrf(String pagePath, String cookies) throws IOException, InterruptedException {
        HttpResponse<String> page = cookies == null ? get(pagePath) : get(pagePath, "Cookie", cookies);
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).as("CSRF field on " + pagePath).isTrue();
        String cookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return new Csrf(field.group(1), cookie);
    }

    private static String storeCookieHeader(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(STORE_COOKIE + "=")).findFirst().orElse(null);
    }

    private static String text(String html) {
        return html.replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ");
    }

    private static int count(String text, String token) {
        return text.split(Pattern.quote(token), -1).length - 1;
    }

    // ================================================================= TC-01: catalog of the whole chain

    @Test
    void tc01_chainCatalogPageListsEachSkuOnceWithItsStores() throws Exception {
        var r=get("/products?q=SIMPLE-PURIFY-150");assertThat(r.statusCode()).isEqualTo(200);String html=r.body();assertThat(html).contains("data-layout=\"client\"","138.000 ₫","Purifying Gel Wash");assertThat(count(html,"<article class=\"pcard\"")).as("one card per SKU").isEqualTo(1);assertThat(html).doesNotContain("VASELINE-LIP-ROSY-7","OneShop Hải Châu");
    }

    @Test
    void chainCatalogSearchAndFilters() throws Exception {
        String byKeyword = get("/products?q=" + enc("purifying gel wash")).body();
        assertThat(byKeyword).contains("Purifying Gel Wash").doesNotContain("CERAVE-HYDRA-236", "MAYB-LIFTER-003");

        String bySku = get("/products?q=cerave-hydra").body();
        assertThat(count(bySku, "<article class=\"pcard\"")).isPositive();
        assertThat(bySku).doesNotContain("Purifying Gel Wash");

        String nothing = get("/products?q=" + enc("không có đâu")).body();
        assertThat(nothing).contains("Không tìm thấy sản phẩm phù hợp.");

        // the form keeps what was searched
        assertThat(byKeyword).contains("value=\"purifying gel wash\"");
    }

    // ================================================================= TC-02: selected Store

    @Test
    void tc02_aStoreCookieNoLongerSwitchesTheCatalog() throws Exception {
        Long goVap = storeId("OS-GOVAP");
        Csrf csrf = csrf("/login", null);

        // the legacy select endpoint still validates and sets the cookie ...
        HttpResponse<String> select = post("/stores/select", FORM, "storeId=" + goVap + "&_csrf=" + csrf.field(),
                "Cookie", csrf.cookie());
        assertThat(select.statusCode()).isEqualTo(302);

        // ... and the product list then shows only what that Store sells, with a way back to the whole chain
        String html = get("/products?q=SIMPLE-PURIFY-150", "Cookie", storeCookie(goVap)).body();
        assertThat(html).contains("id=\"catalog-store-scope\"");
        // SIMPLE-PURIFY-150 is only on sale at Thủ Đức: Gò Vấp shows nothing for it, Thủ Đức shows it
        assertThat(count(html, "<article class=\"pcard\"")).isZero();
        String atThuDuc = get("/products?q=SIMPLE-PURIFY-150", "Cookie", storeCookie(storeId("OS-THUDUC"))).body();
        assertThat(count(atThuDuc, "<article class=\"pcard\"")).isEqualTo(1);
        assertThat(atThuDuc).contains("Còn hàng tại chi nhánh");
        assertThat(get("/products?q=SIMPLE-PURIFY-150").body()).doesNotContain("id=\"catalog-store-scope\"");
    }

    @Test
    void searchAndFiltersIgnoreAStaleStoreCookie() throws Exception {
        String context = storeCookie(storeId("OS-QUAN10"));
        // CERAVE-HYDRA-236 is not sold at Quận 10: hidden while that Store is chosen, present in the whole chain
        assertThat(get("/products?q=hydrating", "Cookie", context).body()).doesNotContain("CERAVE-HYDRA-236")
                .contains("id=\"catalog-store-scope\"");
        assertThat(get("/products?q=hydrating").body()).contains("CERAVE-HYDRA-236");
    }

    @Test
    void storeCannotBeChosenWithARequestParameter() throws Exception {
        // only the validated cookie sets the context: these parameters are ignored
        String html = get("/products?storeId=" + storeId("OS-GOVAP") + "&selectedStoreId=2&store_id=2").body();

        assertThat(html).doesNotContain("data-store-scope");
        assertThat(get("/products?q=SIMPLE-PURIFY-150&storeId=2").body()).contains("SIMPLE-PURIFY-150");
    }

    // ================================================================= TC-03: Product Detail

    @Test
    void tc03_productDetailHasOnePriceWhateverStoreCookieIsPresent() throws Exception {
        String path = "/products/" + productId("SIMPLE-PURIFY-150");
        for (String code : List.of("OS-THUDUC", "OS-GOVAP", "OS-QUAN7")) {
            var r = get(path, "Cookie", storeCookie(storeId(code)));
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).contains("138.000 ₫", "id=\"product-price\"", "Còn hàng toàn hệ thống");
            assertThat(r.body()).doesNotContain("Chi nhánh đang xem", "135.000 ₫", "Chọn chi nhánh này");
        }
    }

    private static String storeLookup(String html) {
        return section(html, "id=\"other-stores\"");
    }

    private static String section(String html, String marker) {
        int start = html.indexOf(marker);
        assertThat(start).as(marker).isPositive();
        return html.substring(start, html.indexOf("</details>", start));
    }

    @Test
    void productDetailListsWhereTheSkuIsInStockAsAnOptionalLookup() throws Exception {
        String html = get("/products/" + productId("SIMPLE-PURIFY-150")).body();

        assertThat(html).contains("id=\"product-price\"", "138.000 ₫", "Kiểm tra tại cửa hàng");
        assertThat(storeLookup(html)).contains("OneShop Thủ Đức").doesNotContain("OneShop Hải Châu", "Chọn chi nhánh này",
                "action=\"/stores/select\"", "₫");
    }

    @Test
    void productDetailStillSellsWhenThePreviouslyChosenStoreDoesNotHoldIt() throws Exception {
        // SIMPLE-MICELLAR-150 is not sold at Gò Vấp; with V3 that no longer matters for buying it
        String html = get("/products/" + productId("SIMPLE-MICELLAR-150"), "Cookie", storeCookie(storeId("OS-GOVAP"))).body();

        assertThat(html).contains("138.000 ₫", "id=\"product-price\"");
        assertThat(storeLookup(html)).doesNotContain("OneShop Gò Vấp");
        assertThat(html).doesNotContain("name=\"storeProductId\"");
    }

    @Test
    void switchingStoreFromTheDetailPageReturnsToItAndLeavesTheCartAlone() throws Exception {
        Map<Long, String> cartBefore = cartSnapshot();
        assertThat(cartBefore).isNotEmpty();
        String path = "/products/" + productId("SIMPLE-PURIFY-150");
        Csrf csrf = csrf("/login", null);

        for (String code : List.of("OS-GOVAP", "OS-QUAN7", "OS-THUDUC")) {
            HttpResponse<String> select = post("/stores/select", FORM,
                    "storeId=" + storeId(code) + "&returnTo=" + enc(path) + "&_csrf=" + csrf.field(),
                    "Cookie", csrf.cookie());
            assertThat(select.statusCode()).isEqualTo(302);
            assertThat(select.headers().firstValue("Location").orElseThrow()).endsWith(path);
        }
        post("/stores/clear", FORM, "_csrf=" + csrf.field(), "Cookie", csrf.cookie());

        // BR-16: no CartItem moved to another Store, changed or disappeared
        assertThat(cartSnapshot()).isEqualTo(cartBefore);
    }

    private Map<Long, String> cartSnapshot() {
        return cartItemRepository.findAll().stream().collect(Collectors.toMap(CartItem::getId,
                item -> item.getProduct().getId() + "x" + item.getQuantity()));
    }

    @Test
    void productsThatAreNotVisibleAnswer404InsideTheClientLayout() throws Exception {
        for (String path : List.of("/products/" + productId("VASELINE-LIP-ROSY-7"), "/products/999999")) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(response.body()).contains("data-layout=\"client\"", "Không tìm thấy");
            assertThat(count(response.body(), "id=\"site-topbar\"")).as("layout applied once").isEqualTo(1);
        }
    }

    // ================================================================= BR-15: no exact quantity for Clients

    @Test
    void clientPagesNeverShowTheExactQuantity() throws Exception {
        Long serum = productId("SIMPLE-PURIFY-150");
        Long thuDuc = storeId("OS-THUDUC");
        int quantity = storeProductRepository.findByStoreIdAndProductId(thuDuc, serum).orElseThrow().getQuantity();
        assertThat(quantity).as("seed quantity of serum at Thủ Đức").isGreaterThan(9);
        Pattern exactQuantity = Pattern.compile("(?<![\\d.])" + quantity + "(?![\\d.])");

        for (String page : List.of(
                get("/products/" + serum, "Cookie", storeCookie(thuDuc)).body(),
                get("/products/" + serum).body(),
                get("/products?q=purifying%20gel%20wash", "Cookie", storeCookie(thuDuc)).body(),
                get("/products?q=purifying%20gel%20wash").body())) {
            assertThat(page).contains("Còn hàng").doesNotContain("Tồn kho", "data-stock", "data-quantity");
            assertThat(text(page)).doesNotContain("Tồn kho", "quantity");
            assertThat(exactQuantity.matcher(text(page)).find()).as("quantity " + quantity + " in the page").isFalse();
        }

        // the Admin does see it
        String adminPage = get("/admin/store-products?storeId=" + thuDuc + "&productId=" + serum, "Cookie", admin()).body();
        assertThat(adminPage).contains("Tồn kho", ">" + quantity + "<");
    }

    @Test
    void br15_assertionCatchesLeakedStockInHtml() {
        int quantity = 40;
        Pattern exactQuantity = Pattern.compile("(?<![\\d.])" + quantity + "(?![\\d.])");

        // 1. Stock number leaked into visible body text
        String leakedNumberHtml = "<div>Còn hàng</div><span>Số lượng còn lại: 40</span>";
        assertThat(exactQuantity.matcher(text(leakedNumberHtml)).find()).isTrue();

        // 2. "Tồn kho" label leaked into page
        String leakedLabelHtml = "<div>Còn hàng</div><span>Tồn kho: 40</span>";
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () ->
                assertThat(leakedLabelHtml).contains("Còn hàng").doesNotContain("Tồn kho", "data-stock", "data-quantity")
        );

        // 3. data-stock or data-quantity attribute leaked
        String leakedAttrHtml = "<div class=\"badge\" data-stock=\"40\">Còn hàng</div>";
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () ->
                assertThat(leakedAttrHtml).contains("Còn hàng").doesNotContain("Tồn kho", "data-stock", "data-quantity")
        );

        // 4. "quantity" English word leaked in visible body text
        String leakedWordHtml = "<div>Còn hàng</div><p>Available quantity: 40</p>";
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () ->
                assertThat(text(leakedWordHtml)).doesNotContain("Tồn kho", "quantity")
        );
    }

    // ================================================================= selected Store: invalid values

    @Test
    void invalidSelectedStoreFallsBackToTheWholeChainAndDropsTheCookie() throws Exception {
        for (Object value : List.of(999999, -1, 0, "abc", "1%20OR%201=1", storeId("OS-HAICHAU"), "")) {
            HttpResponse<String> response = get("/products", "Cookie", storeCookie(value));

            assertThat(response.statusCode()).as("cookie " + value).isEqualTo(200);
            assertThat(response.body()).as("cookie " + value).doesNotContain("data-store-scope", "Chi nhánh đang chọn");
            assertThat(storeCookieHeader(response)).as("cookie " + value).startsWith(STORE_COOKIE + "=;")
                    .contains("Max-Age=0");
        }
    }

    @Test
    void aStoreThatCannotBeSelectedIsRefused() throws Exception {
        Csrf csrf = csrf("/login", null);

        for (String body : List.of("storeId=" + storeId("OS-HAICHAU"), "storeId=999999", "storeId=-5", "")) {
            HttpResponse<String> select = post("/stores/select", FORM, body + "&_csrf=" + csrf.field(),
                    "Cookie", csrf.cookie());

            assertThat(select.statusCode()).as(body).isEqualTo(302);
            assertThat(select.headers().firstValue("Location").orElseThrow()).as(body).endsWith("/stores?error=unavailable");
            assertThat(storeCookieHeader(select)).as(body).isNull();
        }
        // not a number at all
        HttpResponse<String> garbage = post("/stores/select", FORM, "storeId=abc&_csrf=" + csrf.field(),
                "Cookie", csrf.cookie());
        assertThat(garbage.statusCode()).isEqualTo(400);
        assertThat(storeCookieHeader(garbage)).isNull();

        assertThat(get("/stores?error=unavailable").body()).contains("không tồn tại hoặc đang tạm ngừng");
    }

    @Test
    void selectingAStoreNeedsACsrfTokenAndOnlyRedirectsInsideTheSite() throws Exception {
        Long thuDuc = storeId("OS-THUDUC");

        HttpResponse<String> noToken = post("/stores/select", FORM, "storeId=" + thuDuc);
        assertThat(noToken.statusCode()).isEqualTo(403);
        assertThat(storeCookieHeader(noToken)).isNull();

        Csrf csrf = csrf("/login", null);
        for (String target : List.of("https://evil.example/", "//evil.example", "/\\evil.example", "javascript:alert(1)",
                "products")) {
            HttpResponse<String> select = post("/stores/select", FORM,
                    "storeId=" + thuDuc + "&returnTo=" + enc(target) + "&_csrf=" + csrf.field(), "Cookie", csrf.cookie());
            assertThat(select.headers().firstValue("Location").orElseThrow()).as(target)
                    .isEqualTo("http://localhost:" + port + "/products");
        }
    }

    @Test
    void clearingTheStoreGoesBackToTheWholeChain() throws Exception {
        String cookie = storeCookie(storeId("OS-THUDUC"));
        Csrf csrf = csrf("/login", cookie);

        HttpResponse<String> clear = post("/stores/clear", FORM, "_csrf=" + csrf.field(),
                "Cookie", cookie + "; " + csrf.cookie());

        assertThat(clear.statusCode()).isEqualTo(302);
        assertThat(storeCookieHeader(clear)).startsWith(STORE_COOKIE + "=;").contains("Max-Age=0");
    }

    // ================================================================= Store Finder / TC-18

    @Test
    void storeFinderShowsMetadataAndFiltersByProvinceAndArea() throws Exception {
        String all = get("/stores").body();
        assertThat(all).contains("OneShop Thủ Đức", "OneShop Gò Vấp", "OneShop Quận 7", "OneShop Quận 10",
                "101 Võ Văn Ngân", "02811110001", "08:00 - 22:00 hằng ngày", "Giao hàng tận nơi", "Nhận tại cửa hàng",
                "Không giao hàng", "Đang hoạt động");
        // no map, no geolocation
        assertThat(all.toLowerCase()).doesNotContain("maps.google", "geolocation", "latitude", "leaflet", "<iframe");

        String daNang = get("/stores?provinceCity=" + enc("Đà Nẵng")).body();
        assertThat(daNang).contains("OneShop Hải Châu").doesNotContain("OneShop Thủ Đức", "OneShop Gò Vấp");

        String goVap = get("/stores?provinceCity=" + enc("TP. Hồ Chí Minh") + "&area=" + enc("Gò Vấp")).body();
        assertThat(goVap).contains("OneShop Gò Vấp").doesNotContain("OneShop Thủ Đức", "OneShop Hải Châu");

        assertThat(get("/stores?provinceCity=" + enc("Đà Nẵng") + "&area=" + enc("Gò Vấp")).body())
                .contains("Không có chi nhánh nào phù hợp.");
    }

    @Test
    void tc18_inactiveStoreIsShownAsNotOperatingAndCannotBeChosen() throws Exception {
        String daNang = get("/stores?provinceCity=" + enc("Đà Nẵng")).body();

        assertThat(daNang).contains("OneShop Hải Châu", "Tạm ngừng", "đang tạm ngừng hoạt động");
        assertThat(count(daNang, "name=\"storeId\"")).as("no select form for the INACTIVE Store").isZero();

        // V3: the Store list is a lookup, no Store has a select form
        assertThat(count(get("/stores").body(), "name=\"storeId\"")).isZero();
        // the Admin still manages it
        assertThat(get("/admin/stores", "Cookie", admin()).body()).contains("OS-HAICHAU", "Ngừng hoạt động");
    }

    @Test
    void aStoreCookieDoesNotMarkAnythingInTheFinder() throws Exception {
        String html = get("/stores", "Cookie", storeCookie(storeId("OS-QUAN7"))).body();

        assertThat(html).doesNotContain("Đang chọn chi nhánh này", "data-store-scope", "Đang mua sắm tại đây");
        assertThat(count(html, "name=\"storeId\"")).isZero();
    }

    // ================================================================= Admin: protected by the backend

    private List<String> adminPages() {
        Long product = productId("SIMPLE-PURIFY-150");
        Long store = storeId("OS-THUDUC");
        Long storeProduct = storeProductRepository.findByStoreIdAndProductId(store, product).orElseThrow().getId();
        return List.of("/admin/categories", "/admin/brands", "/admin/products", "/admin/products/new",
                "/admin/products/" + product + "/edit", "/admin/stores", "/admin/stores/new",
                "/admin/stores/" + store + "/edit", "/admin/store-products", "/admin/store-products/new",
                "/admin/store-products/" + storeProduct + "/edit");
    }

    @Test
    void adminPagesRenderInTheAdminLayoutForAdmin() throws Exception {
        for (String path : adminPages()) {
            HttpResponse<String> response = get(path, "Cookie", admin());

            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path).contains("data-layout=\"admin\"", "os-sidebar")
                    .doesNotContain("data-layout=\"client\"");
            assertThat(count(response.body(), "aria-current=\"page\"")).as("menu highlight on " + path).isEqualTo(1);
        }
        assertThat(get("/admin/products?q=VASELINE-LIP-ROSY-7", "Cookie", admin()).body())
                .contains("VASELINE-LIP-ROSY-7", "Ngừng hoạt động");
        assertThat(get("/admin/categories", "Cookie", admin()).body()).contains("Chăm sóc da", "Trang điểm");
        assertThat(get("/admin/brands", "Cookie", admin()).body()).contains("CeraVe", "Simple");
    }

    @Test
    void adminPagesAreClosedToEveryoneElse() throws Exception {
        for (String path : adminPages()) {
            HttpResponse<String> guest = get(path);
            assertThat(guest.statusCode()).as("guest " + path).isEqualTo(302);
            assertThat(guest.headers().firstValue("Location").orElseThrow()).endsWith("/login");

            for (String role : List.of(customer(), staff())) {
                HttpResponse<String> response = get(path, "Cookie", role);
                assertThat(response.statusCode()).as(path).isEqualTo(403);
                assertThat(response.body()).as(path).doesNotContain("data-layout=\"admin\"", "SIMPLE-PURIFY-150");
            }
        }
    }

    @Test
    void adminWritesAreRefusedForOtherRolesEvenWithAValidCsrfToken() throws Exception {
        long products = productRepository.count();
        long stores = storeRepository.count();
        long storeProducts = storeProductRepository.count();
        Long thuDuc = storeId("OS-THUDUC");
        Long serumAtThuDuc = storeProductRepository.findByStoreIdAndProductId(thuDuc, productId("SIMPLE-PURIFY-150"))
                .orElseThrow().getId();

        for (String role : List.of(customer(), staff())) {
            Csrf csrf = csrf("/", role);
            String cookies = role + "; " + csrf.cookie();
            String token = "&_csrf=" + csrf.field();

            assertThat(post("/admin/products", FORM, "sku=HACK-1&name=Hack&categoryId=1&brandId=1&status=ACTIVE" + token,
                    "Cookie", cookies).statusCode()).isEqualTo(403);
            assertThat(post("/admin/stores", FORM, "code=HACK&name=Hack&address=x&provinceCity=x&area=x&status=ACTIVE" + token,
                    "Cookie", cookies).statusCode()).isEqualTo(403);
            assertThat(post("/admin/categories", FORM, "name=Hack&status=ACTIVE" + token, "Cookie", cookies).statusCode())
                    .isEqualTo(403);
            assertThat(post("/admin/brands", FORM, "name=Hack&status=ACTIVE" + token, "Cookie", cookies).statusCode())
                    .isEqualTo(403);
            assertThat(post("/admin/store-products/" + serumAtThuDuc, FORM, "price=1&quantity=9999&status=ACTIVE" + token,
                    "Cookie", cookies).statusCode()).isEqualTo(403);
            assertThat(post("/admin/stores/" + thuDuc, FORM,
                    "code=OS-THUDUC&name=Hack&address=x&provinceCity=x&area=x&status=INACTIVE" + token, "Cookie", cookies)
                    .statusCode()).isEqualTo(403);
        }

        assertThat(productRepository.count()).isEqualTo(products);
        assertThat(storeRepository.count()).isEqualTo(stores);
        assertThat(storeProductRepository.count()).isEqualTo(storeProducts);
        assertThat(storeProductRepository.findById(serumAtThuDuc).orElseThrow().getPrice()).isEqualByComparingTo("138000");
        assertThat(storeRepository.findById(thuDuc).orElseThrow().getName()).isEqualTo("OneShop Thủ Đức");
    }

    @Test
    void adminWritesNeedACsrfToken() throws Exception {
        long products = productRepository.count();

        HttpResponse<String> response = post("/admin/products", FORM,
                "sku=NO-CSRF-1&name=NoCsrf&categoryId=1&brandId=1&status=ACTIVE", "Cookie", admin());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(productRepository.count()).isEqualTo(products);
        assertThat(productRepository.existsBySku("NO-CSRF-1")).isFalse();
    }

    // ================================================================= Admin: validation (nothing is written)

    @Test
    void duplicateSkuIsShownOnTheProductForm() throws Exception {
        long products = productRepository.count();
        Csrf csrf = csrf("/admin/products/new", admin());

        HttpResponse<String> response = post("/admin/products", FORM,
                "sku=simple-purify-150&price=100000&name=" + enc("Trùng SKU") + "&categoryId=1&brandId=1&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", admin() + "; " + csrf.cookie());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Mã SKU đã tồn tại", "is-invalid", "value=\"simple-purify-150\"",
                "data-layout=\"admin\"");
        assertThat(productRepository.count()).isEqualTo(products);
    }

    @Test
    void productFormValidatesRequiredFields() throws Exception {
        long products = productRepository.count();
        Csrf csrf = csrf("/admin/products/new", admin());

        HttpResponse<String> response = post("/admin/products", FORM, "sku=&name=&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", admin() + "; " + csrf.cookie());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Vui lòng nhập mã SKU", "Vui lòng nhập tên sản phẩm", "Vui lòng chọn danh mục",
                "Vui lòng chọn thương hiệu");
        assertThat(productRepository.count()).isEqualTo(products);
    }

    @Test
    void duplicateStoreProductIsShownOnTheForm() throws Exception {
        long storeProducts = storeProductRepository.count();
        Csrf csrf = csrf("/admin/store-products/new", admin());

        HttpResponse<String> response = post("/admin/store-products", FORM,
                "storeId=" + storeId("OS-THUDUC") + "&productId=" + productId("SIMPLE-PURIFY-150")
                        + "&quantity=1&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", admin() + "; " + csrf.cookie());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Sản phẩm này đã được gắn với chi nhánh đã chọn");
        assertThat(storeProductRepository.count()).isEqualTo(storeProducts);
    }

    @Test
    void storeProductFormRejectsNegativeQuantity() throws Exception {
        Long thuDuc = storeId("OS-THUDUC");
        Long id = storeProductRepository.findByStoreIdAndProductId(thuDuc, productId("SIMPLE-PURIFY-150")).orElseThrow().getId();
        Csrf csrf = csrf("/admin/store-products/" + id + "/edit", admin());

        HttpResponse<String> response = post("/admin/store-products/" + id, FORM,
                "quantity=-5&status=ACTIVE&_csrf=" + csrf.field(), "Cookie", admin() + "; " + csrf.cookie());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Tồn kho không được âm");
        assertThat(storeProductRepository.findById(id).orElseThrow().getPrice()).isEqualByComparingTo("138000");
        assertThat(storeProductRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(40);
    }

    @Test
    void storeProductFormRejectsAForgedStoreSpecificPriceBeforeCallingTheUpdateService() throws Exception {
        Long thuDuc = storeId("OS-THUDUC");
        Long id = storeProductRepository.findByStoreIdAndProductId(thuDuc, productId("SIMPLE-PURIFY-150")).orElseThrow().getId();
        var before = storeProductRepository.findById(id).orElseThrow();
        Long productId = before.getProduct().getId();
        var onlinePrice = productRepository.findById(productId).orElseThrow().getPrice();
        var legacyStorePrice = before.getPrice();
        long movementsBefore = inventoryMovementRepository.count();
        Csrf csrf = csrf("/admin/store-products/" + id + "/edit", admin());

        HttpResponse<String> response = post("/admin/store-products/" + id, FORM,
                "price=119000&quantity=" + before.getQuantity() + "&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", admin() + "; " + csrf.cookie());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Giá online là giá chung của sản phẩm; không thể sửa giá riêng theo chi nhánh.");
        assertThat(storeProductRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(before.getQuantity());
        assertThat(storeProductRepository.findById(id).orElseThrow().getPrice()).isEqualByComparingTo(legacyStorePrice);
        assertThat(productRepository.findById(productId).orElseThrow().getPrice())
                .isEqualByComparingTo(onlinePrice);
        assertThat(inventoryMovementRepository.count()).isEqualTo(movementsBefore);
    }

    @Test
    void duplicateStoreCodeAndBadPhoneAreShownOnTheStoreForm() throws Exception {
        long stores = storeRepository.count();
        Csrf csrf = csrf("/admin/stores/new", admin());

        HttpResponse<String> duplicate = post("/admin/stores", FORM,
                "code=OS-THUDUC&name=X&address=X&provinceCity=X&area=X&status=ACTIVE&pickupEnabled=true&_csrf=" + csrf.field(),
                "Cookie", admin() + "; " + csrf.cookie());
        assertThat(duplicate.body()).contains("Mã chi nhánh đã tồn tại");

        HttpResponse<String> badPhone = post("/admin/stores", FORM,
                "code=OS-NEW&name=X&address=X&provinceCity=X&area=X&status=ACTIVE&phone=abc&_csrf=" + csrf.field(),
                "Cookie", admin() + "; " + csrf.cookie());
        assertThat(badPhone.body()).contains("Số điện thoại không hợp lệ");

        assertThat(storeRepository.count()).isEqualTo(stores);
    }

    @Test
    void duplicateCategoryAndBrandNamesAreShownOnTheirForms() throws Exception {
        Csrf csrf = csrf("/admin/categories", admin());
        String cookies = admin() + "; " + csrf.cookie();

        assertThat(post("/admin/categories", FORM, "name=" + enc("Chăm sóc da") + "&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", cookies).body()).contains("Tên danh mục đã tồn tại");
        assertThat(post("/admin/brands", FORM, "name=CeraVe&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", cookies).body()).contains("Tên thương hiệu đã tồn tại");
        assertThat(post("/admin/categories", FORM, "name=&status=ACTIVE&_csrf=" + csrf.field(),
                "Cookie", cookies).body()).contains("Vui lòng nhập tên danh mục");
    }

    // ================================================================= Admin: image upload wiring (TC-17)

    @Test
    void imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf() throws Exception {
        // With real credentials this request would upload a file; this test only covers the unconfigured case.
        assumeFalse(cloudinaryProperties.isConfigured(), "Cloudinary is configured: not uploading from a test");
        Long serum = productId("SIMPLE-PURIFY-150");
        long images = productImageRepository.count();
        Csrf csrf = csrf("/admin/products/" + serum + "/edit", admin());
        String boundary = "----oneshop-test-boundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf.field() + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"serum.png\"\r\n"
                + "Content-Type: image/png\r\n\r\nPNGDATA\r\n"
                + "--" + boundary + "--\r\n";

        HttpResponse<String> response = post("/admin/products/" + serum + "/images",
                "multipart/form-data; boundary=" + boundary, body, "Cookie", admin() + "; " + csrf.cookie());

        // past security and CSRF, into the controller, refused by CloudinaryService: nothing stored
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .endsWith("/admin/products/" + serum + "/edit?error=image-storage");
        assertThat(productImageRepository.count()).isEqualTo(images);
        assertThat(get("/admin/products/" + serum + "/edit?error=image-storage", "Cookie", admin()).body())
                .contains("Không thể làm việc với Cloudinary", "id=\"product-images\"");

        // the same upload without the CSRF token never reaches the controller
        String noToken = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"serum.png\"\r\n"
                + "Content-Type: image/png\r\n\r\nPNGDATA\r\n"
                + "--" + boundary + "--\r\n";
        assertThat(post("/admin/products/" + serum + "/images", "multipart/form-data; boundary=" + boundary, noToken,
                "Cookie", admin()).statusCode()).isEqualTo(403);
    }
}
