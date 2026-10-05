package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.entity.CartItem;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.CheckoutSessionRepository;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 end to end: real Tomcat (security filters, CSRF, SiteMesh) on the real SQL Server, requests made the way a
 * browser makes them.
 *
 * <p>Skipped without a configured database. The seed cart of khachhang2 (three lines at three Stores) is only read.
 * Writes go to the cart of khachhang1, which is empty in the seed data and is emptied again after every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class CartHttpDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String ALICE = "khachhang1@example.com";   // empty cart in the seed data
    private static final String BOB = "khachhang2@example.com";     // 3 lines at 3 Stores
    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String SERUM = "COCOON-SERUM-30";

    @LocalServerPort
    private int port;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> tokens = new HashMap<>();
    private Map<Long, String> bobsCartBefore;

    @BeforeEach
    void rememberBobsCart() {
        bobsCartBefore = snapshot(BOB);
        assertThat(rowsOf(ALICE)).as("khachhang1 starts with an empty cart").isEmpty();
    }

    @AfterEach
    void emptyAlicesCartAndCheckBobsIsUntouched() {
        cartItemRepository.deleteAll(rowsOf(ALICE));
        assertThat(snapshot(BOB)).as("seed cart of khachhang2").isEqualTo(bobsCartBefore);
    }

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

    private String login(String email) throws IOException, InterruptedException {
        if (!tokens.containsKey(email)) {
            HttpResponse<String> login = post("/api/auth/login", "application/json",
                    "{\"email\":\"" + email + "\",\"password\":\"" + SEED_PASSWORD + "\"}");
            Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.body());
            assertThat(token.find()).as("login of " + email).isTrue();
            tokens.put(email, token.group(1));
        }
        return "ONESHOP_TOKEN=" + tokens.get(email);
    }

    /** POST of a form as a logged-in browser would send it: JWT cookie + CSRF field and cookie. */
    private HttpResponse<String> postAs(String email, String path, String body) throws IOException, InterruptedException {
        String jwt = login(email);
        HttpResponse<String> page = get("/", "Cookie", jwt);
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(path, FORM, (body.isEmpty() ? "" : body + "&") + "_csrf=" + field.group(1),
                "Cookie", jwt + "; " + csrfCookie);
    }

    private String cartPage(String email, String... extraCookies) throws IOException, InterruptedException {
        String cookies = login(email) + (extraCookies.length == 0 ? "" : "; " + String.join("; ", extraCookies));
        HttpResponse<String> response = get("/cart", "Cookie", cookies);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private Long sp(String sku, String storeCode) {
        return storeProductRepository.findByStoreIdAndProductId(
                storeRepository.findByCode(storeCode).orElseThrow().getId(),
                productRepository.findBySku(sku).orElseThrow().getId()).orElseThrow().getId();
    }

    private List<CartItem> rowsOf(String email) {
        return cartRepository.findByUserEmail(email)
                .map(cart -> cartItemRepository.findByCartIdOrderByIdAsc(cart.getId())).orElseGet(List::of);
    }

    private Map<Long, String> snapshot(String email) {
        return rowsOf(email).stream().collect(Collectors.toMap(CartItem::getId,
                item -> item.getStoreProduct().getId() + "x" + item.getQuantity()));
    }

    /** The store groups of a cart page: store name -> html of that group. */
    private static Map<String, String> groups(String html) {
        Map<String, String> groups = new java.util.LinkedHashMap<>();
        Matcher section = Pattern.compile("(?s)<section class=\"card shadow-sm mb-3 os-cart-group\".*?</section>").matcher(html);
        while (section.find()) {
            Matcher name = Pattern.compile("os-cart-store-name\">([^<]+)<").matcher(section.group());
            assertThat(name.find()).isTrue();
            // the CSRF token of the forms is masked differently on every request
            groups.put(name.group(1), section.group().replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\""));
        }
        return groups;
    }

    private static int count(String text, String token) {
        return text.split(Pattern.quote(token), -1).length - 1;
    }

    private static String text(String html) {
        return html.replaceAll("(?s)<script.*?</script>", " ").replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    // ================================================================= who may use the cart

    @Test
    void guestsAreSentToLoginAndCannotChangeAnything() throws Exception {
        assertThat(get("/cart").statusCode()).isEqualTo(302);
        assertThat(get("/cart/selection?cartItemIds=1").statusCode()).isEqualTo(302);

        // a guest with a valid CSRF token still has no cart to write to
        HttpResponse<String> page = get("/login");
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(field.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.split(";")[0]).findFirst().orElseThrow();
        Long bobsLine = bobsCartBefore.keySet().iterator().next();
        for (String path : List.of("/cart/items", "/cart/items/" + bobsLine, "/cart/items/" + bobsLine + "/delete")) {
            HttpResponse<String> response = post(path, FORM,
                    "storeProductId=" + sp(SERUM, "OS-THUDUC") + "&quantity=1&_csrf=" + field.group(1), "Cookie", csrfCookie);
            assertThat(response.statusCode()).as(path).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/login");
        }
    }

    @Test
    void staffAndAdminHaveNoCart() throws Exception {
        Long bobsLine = bobsCartBefore.keySet().iterator().next();
        for (String account : List.of("staff.thuduc@oneshop.vn", "admin@oneshop.vn")) {
            assertThat(get("/cart", "Cookie", login(account)).statusCode()).as(account).isEqualTo(403);
            assertThat(postAs(account, "/cart/items", "storeProductId=" + sp(SERUM, "OS-THUDUC") + "&quantity=1")
                    .statusCode()).as(account).isEqualTo(403);
            assertThat(postAs(account, "/cart/items/" + bobsLine, "quantity=9").statusCode()).as(account).isEqualTo(403);
            assertThat(postAs(account, "/cart/items/" + bobsLine + "/delete", "").statusCode()).as(account).isEqualTo(403);
            assertThat(cartRepository.findByUserEmail(account)).as("no cart was created for " + account).isEmpty();
        }
    }

    @Test
    void cartWritesNeedACsrfToken() throws Exception {
        String jwt = login(ALICE);
        cartService.addItem(ALICE, new AddCartItemRequest(sp(SERUM, "OS-THUDUC"), 2));
        Long line = rowsOf(ALICE).get(0).getId();

        assertThat(post("/cart/items", FORM, "storeProductId=" + sp(SERUM, "OS-GOVAP") + "&quantity=1", "Cookie", jwt)
                .statusCode()).isEqualTo(403);
        assertThat(post("/cart/items/" + line, FORM, "quantity=5", "Cookie", jwt).statusCode()).isEqualTo(403);
        assertThat(post("/cart/items/" + line + "/delete", FORM, "", "Cookie", jwt).statusCode()).isEqualTo(403);

        assertThat(snapshot(ALICE)).containsExactly(Map.entry(line, sp(SERUM, "OS-THUDUC") + "x2"));
    }

    // ================================================================= TC-04

    @Test
    void tc04_addingTheSameStoreProductTwiceShowsOneLineWithTheSummedQuantity() throws Exception {
        Long serumAtThuDuc = sp(SERUM, "OS-THUDUC");

        HttpResponse<String> first = postAs(ALICE, "/cart/items", "storeProductId=" + serumAtThuDuc + "&quantity=2");
        HttpResponse<String> second = postAs(ALICE, "/cart/items", "storeProductId=" + serumAtThuDuc + "&quantity=1");

        assertThat(first.statusCode()).isEqualTo(302);
        assertThat(first.headers().firstValue("Location").orElseThrow()).endsWith("/cart?success=added");
        assertThat(second.statusCode()).isEqualTo(302);

        assertThat(rowsOf(ALICE)).hasSize(1);
        assertThat(rowsOf(ALICE).get(0).getQuantity()).isEqualTo(3);

        String html = cartPage(ALICE);
        assertThat(count(html, "class=\"list-group-item os-cart-item\"")).isEqualTo(1);
        assertThat(html).contains("data-store-product-id=\"" + serumAtThuDuc + "\"", "name=\"quantity\"", "value=\"3\"",
                "Cocoon Serum Bí Đao 30ml", "129.000 ₫", "387.000 ₫");
        assertThat(get("/cart?success=added", "Cookie", login(ALICE)).body()).contains("Đã thêm sản phẩm vào giỏ hàng.");
    }

    @Test
    void tc04_twoSimultaneousAddsOfTheSameStoreProductStillGiveOneLine() throws Exception {
        Long serumAtThuDuc = sp(SERUM, "OS-THUDUC");
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> clicks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            clicks.add(CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                cartService.addItem(ALICE, new AddCartItemRequest(serumAtThuDuc, 1));
            }));
        }
        start.countDown();
        CompletableFuture.allOf(clicks.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

        // no duplicate line and no lost update
        assertThat(rowsOf(ALICE)).hasSize(1);
        assertThat(rowsOf(ALICE).get(0).getQuantity()).isEqualTo(4);
    }

    // ================================================================= TC-05

    @Test
    void tc05_cartPageShowsOneGroupPerStoreWithItsOwnLines() throws Exception {
        String html = cartPage(BOB);
        Map<String, String> groups = groups(html);

        assertThat(groups.keySet()).containsExactly("OneShop Gò Vấp", "OneShop Quận 7", "OneShop Thủ Đức");
        // each line is in the group of its own Store and nowhere else
        assertThat(groups.get("OneShop Thủ Đức")).contains("INNI-TONER-200").doesNotContain("BBIA-CHEEK");
        assertThat(groups.get("OneShop Gò Vấp")).contains("BBIA-CHEEK-08").doesNotContain("INNI-TONER-200", "BBIA-CHEEK-06");
        assertThat(groups.get("OneShop Quận 7")).contains("BBIA-CHEEK-06").doesNotContain("INNI-TONER-200", "BBIA-CHEEK-08");
        assertThat(count(html, "class=\"list-group-item os-cart-item\"")).isEqualTo(3);
        for (String sku : List.of("INNI-TONER-200", "BBIA-CHEEK-08", "BBIA-CHEEK-06")) {
            assertThat(count(html, ">" + sku + "<")).as(sku).isEqualTo(1);
        }
        assertThat(html).contains("sản phẩm từ <strong>3</strong> chi nhánh", "255.000 ₫", "195.000 ₫", "378.000 ₫",
                "828.000 ₫", "data-layout=\"client\"");

        // one "select the whole Store" checkbox per group, one checkbox per line carrying its cartItemId
        assertThat(count(html, "os-cart-group-toggle\"")).isEqualTo(3);
        assertThat(count(html, ">Chọn cả chi nhánh</label>")).isEqualTo(3);
        for (Long cartItemId : bobsCartBefore.keySet()) {
            assertThat(Pattern.compile("<input type=\"checkbox\"[^>]*name=\"cartItemIds\"[^>]*form=\"cart-selection-form\"[^>]*value=\""
                    + cartItemId + "\"").matcher(html).find()).as("checkbox of line " + cartItemId).isTrue();
        }
        assertThat(html).contains("id=\"cart-selection-form\"", "action=\"/cart/selection\"");
    }

    @Test
    void sameProductAtTwoStoresIsTwoLinesInTwoGroups() throws Exception {
        postAs(ALICE, "/cart/items", "storeProductId=" + sp(SERUM, "OS-THUDUC") + "&quantity=1");
        postAs(ALICE, "/cart/items", "storeProductId=" + sp(SERUM, "OS-GOVAP") + "&quantity=2");
        postAs(ALICE, "/cart/items", "storeProductId=" + sp(SERUM, "OS-QUAN10") + "&quantity=1");

        Map<String, String> groups = groups(cartPage(ALICE));

        assertThat(groups.keySet()).containsExactly("OneShop Gò Vấp", "OneShop Quận 10", "OneShop Thủ Đức");
        assertThat(groups.get("OneShop Thủ Đức")).contains(SERUM, "129.000 ₫");
        assertThat(groups.get("OneShop Gò Vấp")).contains(SERUM, "135.000 ₫", "270.000 ₫");
        assertThat(groups.get("OneShop Quận 10")).contains(SERUM, "139.000 ₫");
        assertThat(rowsOf(ALICE)).hasSize(3);
    }

    // ================================================================= what the server trusts

    @Test
    void userPriceAndStoreSentByTheClientAreIgnored() throws Exception {
        Long serumAtThuDuc = sp(SERUM, "OS-THUDUC");
        Long bobsCart = cartRepository.findByUserEmail(BOB).orElseThrow().getId();
        Long bobsUser = cartRepository.findByUserEmail(BOB).orElseThrow().getUser().getId();

        HttpResponse<String> response = postAs(ALICE, "/cart/items", "storeProductId=" + serumAtThuDuc + "&quantity=1"
                + "&userId=" + bobsUser + "&user.id=" + bobsUser + "&cartId=" + bobsCart + "&cart.id=" + bobsCart
                + "&price=1&unitPrice=1&subtotal=1&totalAmount=1&storeId=2&productId=1&status=AVAILABLE");

        assertThat(response.statusCode()).isEqualTo(302);
        // it went into the cart of the logged-in customer, at the real price of that StoreProduct
        assertThat(rowsOf(ALICE)).singleElement().extracting(item -> item.getStoreProduct().getId()).isEqualTo(serumAtThuDuc);
        assertThat(cartPage(ALICE)).contains("129.000 ₫").doesNotContain("1 ₫");
    }

    @Test
    void addIsRefusedForWhatCannotBeBoughtAndTheReasonIsShown() throws Exception {
        // more than the Store has (cheek #08 at Thủ Đức: seed stock 8)
        HttpResponse<String> tooMany = postAs(ALICE, "/cart/items", "storeProductId=" + sp("BBIA-CHEEK-08", "OS-THUDUC") + "&quantity=9");
        assertThat(tooMany.statusCode()).isEqualTo(400);
        assertThat(tooMany.body()).contains("id=\"cart-error\"", "không đủ hàng", "data-layout=\"client\"");

        // out of stock, StoreProduct INACTIVE, Store INACTIVE, Product INACTIVE, unknown, missing, bad quantity
        for (String body : List.of(
                "storeProductId=" + sp(SERUM, "OS-QUAN7") + "&quantity=1",
                "storeProductId=" + sp("INNI-TONER-200", "OS-QUAN10") + "&quantity=1",
                "storeProductId=" + sp(SERUM, "OS-HAICHAU") + "&quantity=1",
                "storeProductId=" + sp("COCOON-LIP-05", "OS-THUDUC") + "&quantity=1",
                "storeProductId=999999&quantity=1",
                "quantity=1",
                "storeProductId=" + sp(SERUM, "OS-THUDUC") + "&quantity=0",
                "storeProductId=" + sp(SERUM, "OS-THUDUC") + "&quantity=-2",
                "storeProductId=" + sp(SERUM, "OS-THUDUC"),
                "productId=" + productRepository.findBySku(SERUM).orElseThrow().getId() + "&quantity=1")) {
            HttpResponse<String> response = postAs(ALICE, "/cart/items", body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.body()).as(body).contains("id=\"cart-error\"").doesNotContain("Exception", "SQL", "at com.oneshop");
        }
        assertThat(rowsOf(ALICE)).isEmpty();
    }

    // ================================================================= update / remove

    @Test
    void updateAndRemoveThroughTheCartForms() throws Exception {
        cartService.addItem(ALICE, new AddCartItemRequest(sp("BBIA-CHEEK-08", "OS-THUDUC"), 2));   // seed stock 8
        Long line = rowsOf(ALICE).get(0).getId();

        HttpResponse<String> update = postAs(ALICE, "/cart/items/" + line, "quantity=8");
        assertThat(update.statusCode()).isEqualTo(302);
        assertThat(update.headers().firstValue("Location").orElseThrow()).endsWith("/cart?success=updated");
        assertThat(rowsOf(ALICE).get(0).getQuantity()).isEqualTo(8);

        // above the stock: refused, reason shown, the cart still shows the old quantity
        HttpResponse<String> tooMany = postAs(ALICE, "/cart/items/" + line, "quantity=9");
        assertThat(tooMany.statusCode()).isEqualTo(400);
        assertThat(tooMany.body()).contains("id=\"cart-error\"", "không đủ hàng", "value=\"8\"");
        for (String body : List.of("quantity=0", "quantity=-1", "quantity=abc", "")) {
            HttpResponse<String> invalid = postAs(ALICE, "/cart/items/" + line, body);
            assertThat(invalid.statusCode()).as(body).isEqualTo(400);
        }
        assertThat(rowsOf(ALICE).get(0).getQuantity()).isEqualTo(8);

        HttpResponse<String> remove = postAs(ALICE, "/cart/items/" + line + "/delete", "");
        assertThat(remove.statusCode()).isEqualTo(302);
        assertThat(remove.headers().firstValue("Location").orElseThrow()).endsWith("/cart?success=removed");
        assertThat(rowsOf(ALICE)).isEmpty();
        assertThat(cartPage(ALICE)).contains("id=\"cart-empty\"", "Giỏ hàng của bạn đang trống.");
    }

    @Test
    void aCustomerCannotChangeAnotherCustomersLineByItsId() throws Exception {
        for (Long bobsLine : bobsCartBefore.keySet()) {
            HttpResponse<String> update = postAs(ALICE, "/cart/items/" + bobsLine, "quantity=1");
            assertThat(update.statusCode()).isEqualTo(404);
            assertThat(update.body()).contains("Không tìm thấy sản phẩm này trong giỏ hàng của bạn.");

            assertThat(postAs(ALICE, "/cart/items/" + bobsLine + "/delete", "").statusCode()).isEqualTo(404);
            assertThat(get("/cart/selection?cartItemIds=" + bobsLine, "Cookie", login(ALICE)).statusCode()).isEqualTo(400);
        }
        // and Alice's cart page shows nothing of Bob's
        assertThat(cartPage(ALICE)).contains("id=\"cart-empty\"").doesNotContain("INNI-TONER-200", "BBIA-CHEEK");
        assertThat(snapshot(BOB)).isEqualTo(bobsCartBefore);
    }

    // ================================================================= selected Store (BR-16)

    @Test
    void changingTheSelectedStoreLeavesTheCartExactlyAsItWas() throws Exception {
        String chain = cartPage(BOB);

        for (String code : List.of("OS-QUAN10", "OS-THUDUC", "OS-GOVAP")) {
            Long storeId = storeRepository.findByCode(code).orElseThrow().getId();
            // choosing the Store through the real form, as the logged-in customer
            HttpResponse<String> select = postAs(BOB, "/stores/select", "storeId=" + storeId);
            assertThat(select.statusCode()).isEqualTo(302);

            String withStore = cartPage(BOB, "ONESHOP_STORE=" + storeId);
            assertThat(withStore).contains("data-store-scope=\"store\"");
            assertThat(groups(withStore)).isEqualTo(groups(chain));
            assertThat(snapshot(BOB)).isEqualTo(bobsCartBefore);
        }
    }

    // ================================================================= hand-over to checkout

    @Test
    void selectedCartItemIdsAreHandedOverGroupedByStoreAndNothingIsCreated() throws Exception {
        long orders = orderRepository.count();
        long checkouts = checkoutSessionRepository.count();
        long movements = movementRepository.count();
        List<Long> lines = new ArrayList<>(bobsCartBefore.keySet());

        // what the cart form sends when two checkboxes are ticked
        HttpResponse<String> two = get("/cart/selection?cartItemIds=" + lines.get(0) + "&cartItemIds=" + lines.get(1),
                "Cookie", login(BOB));
        assertThat(two.statusCode()).isEqualTo(200);
        assertThat(count(two.body(), "os-selection-group\"")).isEqualTo(2);
        assertThat(two.body()).contains("data-cart-item-id=\"" + lines.get(0) + "\"", "data-cart-item-id=\"" + lines.get(1) + "\"")
                .doesNotContain("data-cart-item-id=\"" + lines.get(2) + "\"");

        HttpResponse<String> all = get("/cart/selection?" + lines.stream().map(id -> "cartItemIds=" + id)
                .collect(Collectors.joining("&")), "Cookie", login(BOB));
        assertThat(count(all.body(), "os-selection-group\"")).isEqualTo(3);
        assertThat(all.body()).contains("828.000 ₫", "<strong>3</strong> sản phẩm");

        // nothing ticked
        HttpResponse<String> none = get("/cart/selection", "Cookie", login(BOB));
        assertThat(none.statusCode()).isEqualTo(400);
        assertThat(none.body()).contains("id=\"cart-error\"", "Vui lòng chọn ít nhất một sản phẩm");

        assertThat(orderRepository.count()).isEqualTo(orders);
        assertThat(checkoutSessionRepository.count()).isEqualTo(checkouts);
        assertThat(movementRepository.count()).isEqualTo(movements);
    }

    // ================================================================= stock: checked, never held or shown

    @Test
    void addingToTheCartDoesNotChangeStockAndTheCartPageDoesNotShowIt() throws Exception {
        Long cheek = sp("BBIA-CHEEK-08", "OS-THUDUC");
        int stock = storeProductRepository.findById(cheek).orElseThrow().getQuantity();
        long movements = movementRepository.count();

        postAs(ALICE, "/cart/items", "storeProductId=" + cheek + "&quantity=3");
        postAs(ALICE, "/cart/items", "storeProductId=" + sp(SERUM, "OS-THUDUC") + "&quantity=2");

        assertThat(storeProductRepository.findById(cheek).orElseThrow().getQuantity()).isEqualTo(stock);
        assertThat(movementRepository.count()).isEqualTo(movements);

        // neither the cheek stock (8) nor the serum stock (40) appears anywhere on the page
        String page = text(cartPage(ALICE));
        int serumStock = storeProductRepository.findById(sp(SERUM, "OS-THUDUC")).orElseThrow().getQuantity();
        for (int exact : new int[]{stock, serumStock}) {
            assertThat(Pattern.compile("(?<![\\d.])" + exact + "(?![\\d.])").matcher(page).find())
                    .as("exact stock " + exact + " on the cart page").isFalse();
        }
        assertThat(page).doesNotContain("Tồn kho", "còn lại");
    }

    // ================================================================= Product Detail -> cart

    @Test
    void productDetailOffersAddToCartOnlyForAConcreteStoreProduct() throws Exception {
        Long serum = productRepository.findBySku(SERUM).orElseThrow().getId();
        Long thuDuc = storeRepository.findByCode("OS-THUDUC").orElseThrow().getId();
        Long quan7 = storeRepository.findByCode("OS-QUAN7").orElseThrow().getId();
        String path = "/products/" + serum;

        // customer + selected Store selling it: a form that carries the StoreProduct id and a quantity, no price
        String customer = get(path, "Cookie", login(ALICE) + "; ONESHOP_STORE=" + thuDuc).body();
        String form = customer.substring(customer.indexOf("id=\"add-to-cart\""), customer.indexOf("</form>", customer.indexOf("id=\"add-to-cart\"")));
        assertThat(form).contains("action=\"/cart/items\"", "name=\"storeProductId\" value=\"" + sp(SERUM, "OS-THUDUC") + "\"",
                "name=\"quantity\"", "name=\"_csrf\"", "Thêm vào giỏ");
        assertThat(form).doesNotContain("name=\"price\"", "name=\"productId\"", "name=\"storeId\"", "name=\"userId\"");

        // guest: asked to log in, no form
        String guest = get(path, "Cookie", "ONESHOP_STORE=" + thuDuc).body();
        assertThat(guest).contains("Đăng nhập để thêm vào giỏ").doesNotContain("action=\"/cart/items\"");

        // out of stock at the selected Store: no way to add
        String soldOut = get(path, "Cookie", login(ALICE) + "; ONESHOP_STORE=" + quan7).body();
        assertThat(soldOut).contains("Tạm hết hàng").doesNotContain("action=\"/cart/items\"", "id=\"add-to-cart\"");

        // no Store selected: the customer must choose a Store first; a product alone cannot be added
        String noStore = get(path, "Cookie", login(ALICE)).body();
        assertThat(noStore).contains("id=\"no-store-selected\"", "Chọn chi nhánh này")
                .doesNotContain("action=\"/cart/items\"", "id=\"add-to-cart\"");

        // staff and admin get no add form
        String staff = get(path, "Cookie", login("staff.thuduc@oneshop.vn") + "; ONESHOP_STORE=" + thuDuc).body();
        assertThat(staff).doesNotContain("action=\"/cart/items\"", "Đăng nhập để thêm vào giỏ");
    }
}
