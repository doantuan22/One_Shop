package com.oneshop;

import com.oneshop.dto.response.CartResponse;
import com.oneshop.repository.RoleRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import org.junit.jupiter.api.BeforeEach;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import com.oneshop.service.StoreService;
import com.oneshop.service.StaffOperationsService;
import com.oneshop.dto.response.StaffDashboardResponse;
import com.oneshop.dto.response.StaffStoreDashboardResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Runs the real embedded Tomcat (so SiteMesh and the security filter chain are active) with the
 * database-facing beans mocked: no SQL Server is needed to run the tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    @MockitoBean
    protected StoreProductService storeProductService;

    @MockitoBean
    protected CartService cartService;

    @MockitoBean
    protected CheckoutService checkoutService;

    /** Unstubbed, every customer has an empty cart. */
    @BeforeEach
    void emptyCartByDefault() {
        when(cartService.getCart(any())).thenReturn(CartResponse.empty());
    }

    @MockitoBean
    protected ProductService productService;

    @MockitoBean
    protected UserRepository userRepository;

    @MockitoBean
    protected RoleRepository roleRepository;

    /** There is no database here, so {@code @Transactional} services must not try to open a connection. */
    @MockitoBean
    protected PlatformTransactionManager transactionManager;

    /** Unstubbed, a Staff has no assigned Store (empty list). */
    @MockitoBean
    protected StoreService storeService;

    /** Layout/security tests stay independent of SQL; actual scope/data reads are covered by database tests. */
    @MockitoBean
    protected StaffOperationsService staffOperationsService;

    @MockitoBean
    protected com.oneshop.service.StaffInventoryService staffInventoryService;

    /** Admin dashboard became a real reader in Phase 11; layout tests remain independent of SQL. */
    @MockitoBean
    protected com.oneshop.service.AdminOperationsService adminOperationsService;

    @BeforeEach
    void staffReadModelsByDefault() {
        when(staffOperationsService.getDashboard()).thenAnswer(invocation -> new StaffDashboardResponse(
                storeService.getAssignedStores(SecurityContextHolder.getContext().getAuthentication().getName()).stream()
                        .map(s -> new StaffStoreDashboardResponse(s, 0, 0, 0, 0)).toList(), 5, java.util.List.of()));
        when(staffOperationsService.getOrders(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Page.empty());
        when(staffOperationsService.getPickupQueue(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Page.empty());
        when(staffInventoryService.getStock(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Page.empty());
    }

    /** Redirects are not followed so that tests can assert on them. */
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    protected HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return send(request);
    }

    protected HttpResponse<String> post(String path, String contentType, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
