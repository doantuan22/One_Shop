package com.oneshop;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.DeliveryFulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Tomcat, SiteMesh and security, with mocked domain for isolated UI/CSRF/route verification. */
class DeliveryWebIntegrationTest extends AbstractIntegrationTest {
    private static final String STAFF = "staff@oneshop.test";
    private static final String PATH = "/staff/orders/delivery";
    @MockitoBean private DeliveryFulfillmentService delivery;
    @Autowired private JwtService jwt;
    @Autowired private CustomUserDetailsService users;

    @BeforeEach
    void scope() {
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of(new StoreResponse(1L, "TD", "OneShop Thủ Đức",
                "1 Đường thử", "HCM", "Thủ Đức", null, null, true, true, ActiveStatus.ACTIVE)));
    }

    private String cookie(RoleName role) {
        User user = new User();
        user.setEmail(STAFF);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(STAFF)).thenReturn(Optional.of(user));
        withId(user);
        return "ONESHOP_TOKEN=" + jwt.generateToken(users.loadUserByUsername(STAFF));
    }

    private DeliveryOrderResponse row(OrderStatus status, DeliveryAction action) {
        return new DeliveryOrderResponse(10L, 1L, "OneShop Thủ Đức", LocalDateTime.now(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, OrderPaymentStatus.UNPAID, status, "Người nhận", "0912345678", "1 Đường thử",
                new BigDecimal("175000"), action);
    }

    private void detail(DeliveryOrderResponse row) {
        when(delivery.getDeliveryOrder(STAFF, 10L)).thenReturn(new DeliveryOrderDetailResponse(row,
                List.of(new OrderItemResponse("Snapshot <script>alert(1)</script>", new BigDecimal("175000"), 1, new BigDecimal("175000"))),
                List.of(new OrderHistoryResponse(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "Nhân viên A", "Đã nhận xử lý", LocalDateTime.now())), List.of()));
    }

    private HttpResponse<String> postAs(String cookie, String suffix, String body) throws Exception {
        var page = get("/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(PATH + suffix, "application/x-www-form-urlencoded", body + "&_csrf=" + csrf.group(1),
                "Cookie", cookie + "; " + tokenCookie);
    }

    @Test
    void everyWriteRequiresCsrfEvenWithBearer() throws Exception {
        String staff = cookie(RoleName.STAFF);
        String bearer = "Bearer " + staff.substring("ONESHOP_TOKEN=".length());
        for (var action : DeliveryAction.values()) {
            String path = PATH + "/10/" + action.getPath();
            assertThat(post(path, "application/x-www-form-urlencoded", "", "Cookie", staff).statusCode()).isEqualTo(403);
            assertThat(post(path, "application/x-www-form-urlencoded", "", "Authorization", bearer).statusCode()).isEqualTo(403);
            for (String encodedRoot : List.of("/%73taff/orders/delivery", "/staff/%6frders/delivery", "/staff/orders/%64elivery")) {
                assertThat(post(encodedRoot + "/10/" + action.getPath(), "application/x-www-form-urlencoded", "", "Authorization", bearer).statusCode())
                        .as("CSRF is required for the same route with percent-encoded path segments").isEqualTo(403);
            }
        }
        verifyNoInteractions(delivery);
    }

    @Test
    void customerAdminGuestAndStaffWithoutAssignmentCannotReadOrMutate() throws Exception {
        for (var role : List.of(RoleName.CUSTOMER, RoleName.ADMIN)) {
            String account = cookie(role);
            assertThat(get(PATH, "Cookie", account).statusCode()).isEqualTo(403);
            assertThat(get(PATH + "/10", "Cookie", account).statusCode()).isEqualTo(403);
            for (var action : DeliveryAction.values()) assertThat(postAs(account, "/10/" + action.getPath(), "").statusCode()).isEqualTo(403);
        }
        assertThat(get(PATH).statusCode()).isEqualTo(302);
        for (var action : DeliveryAction.values()) assertThat(postAs("", "/10/" + action.getPath(), "").statusCode()).isEqualTo(302);
        String staff = cookie(RoleName.STAFF);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of());
        assertThat(get(PATH, "Cookie", staff).statusCode()).isEqualTo(403);
        for (var action : DeliveryAction.values()) assertThat(postAs(staff, "/10/" + action.getPath(), "").statusCode()).isEqualTo(403);
        verifyNoInteractions(delivery);
    }

}
