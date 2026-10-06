package com.oneshop;

import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.entity.*;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real web/security/layout with a mocked service; runnable without SQL Server. */
class PaymentWebIntegrationTest extends AbstractIntegrationTest {
    private static final String EMAIL = "customer@oneshop.test";
    private static final String PATH = "/orders/10/payments";
    @MockitoBean private PaymentService payments;
    @Autowired private JwtService jwt;
    @Autowired private CustomUserDetailsService users;

    private String cookie(RoleName role) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return "ONESHOP_TOKEN=" + jwt.generateToken(users.loadUserByUsername(EMAIL));
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
    void viewHasOrderStoreTotalAndPendingControls() throws Exception {
        when(payments.getOrderPayments(EMAIL, 10L)).thenReturn(new OrderPaymentResponse(10L, 3L, "OneShop Thủ Đức",
                new BigDecimal("200000"), PaymentMethod.ONLINE, OrderPaymentStatus.UNPAID, OrderStatus.PENDING_PAYMENT,
                List.of(new PaymentResponse(20L, 10L, PaymentMethod.ONLINE, new BigDecimal("200000"), PaymentStatus.PENDING,
                        null, null, LocalDateTime.now()))));
        var response = get(PATH, "Cookie", cookie(RoleName.CUSTOMER));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("OneShop Thủ Đức", "200.000 ₫", "Mô phỏng thành công", "Mô phỏng thất bại",
                "/orders/10/payments/20/success", "/orders/10/payments/20/failure", "name=\"_csrf\"");
    }

    @Test
    void formsUsePrincipalAndIgnoreAllClientPaymentData() throws Exception {
        String cookie = cookie(RoleName.CUSTOMER);
        String forged = "amount=1&method=COD&userId=999&status=SUCCESS&paymentStatus=PAID&orderStatus=CANCELLED";
        assertThat(postAs(cookie, "/attempts", forged).statusCode()).isEqualTo(302);
        assertThat(postAs(cookie, "/20/success", forged).statusCode()).isEqualTo(302);
        assertThat(postAs(cookie, "/20/failure", forged).statusCode()).isEqualTo(302);
        verify(payments).createOnlinePaymentAttempt(EMAIL, 10L);
        verify(payments).markOnlinePaymentSuccess(EMAIL, 10L, 20L);
        verify(payments).markOnlinePaymentFailed(EMAIL, 10L, 20L);
    }

    @Test
    void csrfIsRequiredOnEveryWrite() throws Exception {
        String cookie = cookie(RoleName.CUSTOMER);
        for (String suffix : List.of("/attempts", "/20/success", "/20/failure")) {
            assertThat(post(PATH + suffix, "application/x-www-form-urlencoded", "", "Cookie", cookie).statusCode()).isEqualTo(403);
        }
        verifyNoInteractions(payments);
    }

    @Test
    void staffAdminAndGuestCannotUseClientFlow() throws Exception {
        for (RoleName role : List.of(RoleName.STAFF, RoleName.ADMIN)) {
            String cookie = cookie(role);
            assertThat(get(PATH, "Cookie", cookie).statusCode()).isEqualTo(403);
            for (String suffix : List.of("/attempts", "/20/success", "/20/failure")) {
                assertThat(postAs(cookie, suffix, "").statusCode()).isEqualTo(403);
            }
        }
        assertThat(get(PATH).statusCode()).isEqualTo(302);
        verifyNoInteractions(payments);
    }

    @Test
    void unknownOwnerOrAttemptGetsFriendly404() throws Exception {
        when(payments.getOrderPayments(EMAIL, 10L)).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var response = get(PATH, "Cookie", cookie(RoleName.CUSTOMER));
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Không tìm thấy đơn hàng");
    }

    @Test
    void lockConflictAndConstraintFailureDoNotExposeSqlToUi() throws Exception {
        String cookie = cookie(RoleName.CUSTOMER);
        when(payments.createOnlinePaymentAttempt(EMAIL, 10L)).thenThrow(new CannotAcquireLockException("SECRET SQL STACK"));
        var response = postAs(cookie, "/attempts", "");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("Đơn hàng đang được xử lý").doesNotContain("SECRET SQL STACK");
        doThrow(new DataIntegrityViolationException("PRIVATE CONSTRAINT")).when(payments).createOnlinePaymentAttempt(EMAIL, 10L);
        response = postAs(cookie, "/attempts", "");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("Không thể lưu kết quả thanh toán").doesNotContain("PRIVATE CONSTRAINT");
    }
}
