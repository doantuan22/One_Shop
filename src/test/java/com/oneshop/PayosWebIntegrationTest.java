package com.oneshop;

import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.PaymentService;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.payos.PayosApi;
import com.oneshop.service.payment.payos.PayosPaymentFlow;
import com.oneshop.service.payment.payos.PayosWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The payOS HTTP surface with the real security chain (no SQL Server): public signed webhook, safe return pages. */
@TestPropertySource(properties = {
        "oneshop.payment.provider=PAYOS",
        "oneshop.payos.client-id=test-client",
        "oneshop.payos.api-key=test-key",
        "oneshop.payos.checksum-key=test-checksum",
        "oneshop.payos.public-base-url=https://shop.example.com"
})
class PayosWebIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "customer@oneshop.test";

    @MockitoBean private PayosApi api;
    @MockitoBean private PayosWebhookService webhooks;
    @MockitoBean private PayosPaymentFlow flow;
    @MockitoBean private PaymentService payments;
    @MockitoBean private PaymentReconciliationService reconciliation;
    @Autowired private JwtService jwt;
    @Autowired private CustomUserDetailsService users;

    private String customerCookie() {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash("x");
        user.setRole(new Role(RoleName.CUSTOMER));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        withId(user);
        return "ONESHOP_TOKEN=" + jwt.generateToken(users.loadUserByUsername(EMAIL));
    }

    // ------------------------------------------------------------------ webhook

    @Test
    void theWebhookNeedsNeitherLoginNorCsrfAndAcknowledgesAVerifiedDelivery() throws Exception {
        when(webhooks.handle(anyString())).thenReturn(PayosWebhookService.Result.ACCEPTED);

        var response = post("/api/payments/payos/webhook", "application/json", "{\"code\":\"00\",\"success\":true}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"success\":true");
        verify(webhooks).handle("{\"code\":\"00\",\"success\":true}");
    }

    @Test
    void aWebhookThatFailsSignatureVerificationIsRefusedWith400() throws Exception {
        when(webhooks.handle(anyString())).thenReturn(PayosWebhookService.Result.INVALID_SIGNATURE);
        assertThat(post("/api/payments/payos/webhook", "application/json", "{}").statusCode()).isEqualTo(400);
    }

    @Test
    void aProcessingFailureIsNotAcknowledgedSoPayosRetries() throws Exception {
        when(webhooks.handle(anyString())).thenThrow(new IllegalStateException("db down"));
        assertThat(post("/api/payments/payos/webhook", "application/json", "{}").statusCode()).isEqualTo(500);
    }

    @Test
    void csrfStaysRequiredOnTheCustomersOtherWrites() throws Exception {
        String cookie = customerCookie();
        assertThat(post("/orders/10/payments/attempts", "application/x-www-form-urlencoded", "", "Cookie", cookie)
                .statusCode()).isEqualTo(403);
        assertThat(post("/orders/10/cancel", "application/x-www-form-urlencoded", "", "Cookie", cookie)
                .statusCode()).isEqualTo(403);
        assertThat(post("/api/payments/payos/webhook-other", "application/json", "{}").statusCode()).isIn(401, 403, 404);
        verifyNoInteractions(flow);
    }

    // ------------------------------------------------------------------ return / cancel pages

    @Test
    void aForgedReturnUrlMarksNothingPaidItOnlyAsksPayos() throws Exception {
        String cookie = customerCookie();
        when(flow.sync(anyLong(), any())).thenReturn(PaymentOutcome.AWAITING);

        var response = get("/orders/10/payments/payos/return?status=PAID&cancel=false&code=00&id=forged&orderCode=10&amount=1",
                "Cookie", cookie);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/orders/10/payments?outcome=AWAITING");
        verify(flow).sync(10L, PaymentReconciliationService.Trigger.CUSTOMER_RETURN);
        // nothing in the query string reaches any payment decision
        verify(reconciliation, never()).reconcileLocked(any(), any(), any());
        verify(reconciliation, never()).simulate(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void theCancelPageDoesNotCancelTheOrderEither() throws Exception {
        String cookie = customerCookie();
        when(flow.sync(anyLong(), any())).thenReturn(PaymentOutcome.LATE_PAYMENT_REFUNDED);

        var response = get("/orders/10/payments/payos/cancel?cancel=true&status=CANCELLED", "Cookie", cookie);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("payosCancelled")
                .contains("outcome=LATE_PAYMENT_REFUNDED");
        verify(flow).sync(10L, PaymentReconciliationService.Trigger.CUSTOMER_RETURN);
    }

    @Test
    void theReturnPageIsOnlyForTheOrdersOwnCustomerAndNeedsLogin() throws Exception {
        assertThat(get("/orders/10/payments/payos/return").statusCode()).isIn(302, 401, 403);
        verifyNoInteractions(flow);
    }

    @Test
    void aFailingPayosQueryOnReturnShowsReconcilingNotSuccess() throws Exception {
        String cookie = customerCookie();
        when(flow.sync(anyLong(), any())).thenThrow(new IllegalStateException("payOS down"));

        var response = get("/orders/10/payments/payos/return", "Cookie", cookie);

        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("outcome=RECONCILING");
    }

    // ------------------------------------------------------------------ payment page

    @Test
    void thePayButtonSendsTheCustomerToTheCheckoutUrlPayosReturned() throws Exception {
        String cookie = customerCookie();
        when(flow.start(EMAIL, 10L)).thenReturn(new PayosPaymentFlow.StartResult("https://pay.payos.vn/web/abc"));

        var page = get("/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        var response = post("/orders/10/payments/attempts", "application/x-www-form-urlencoded",
                "amount=1&status=SUCCESS&_csrf=" + csrf.group(1), "Cookie", cookie + "; " + tokenCookie);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).contains("https://pay.payos.vn/web/abc");
        verify(reconciliation, never()).simulate(anyString(), anyLong(), anyLong(), any()); // no simulated success
        verifyNoInteractions(api);
    }

    @Test
    void thePaymentPageOffersPayosCheckoutAndNoSimulationButtons() throws Exception {
        String cookie = customerCookie();
        when(payments.getOrderPayments(EMAIL, 10L)).thenReturn(new OrderPaymentResponse(10L, 3L, "OneShop",
                new BigDecimal("200000"), PaymentMethod.ONLINE, OrderPaymentStatus.UNPAID, OrderStatus.PENDING_PAYMENT,
                List.of(), LocalDateTime.now().plusMinutes(10), false, false, null, false, true));
        when(flow.sync(anyLong(), any())).thenReturn(PaymentOutcome.AWAITING);

        String body = get("/orders/10/payments", "Cookie", cookie).body();

        assertThat(body).contains("id=\"start-payment\"", "Thanh toán qua payOS").doesNotContain("payment-gateway-off");
        assertThat(body).doesNotContain("/simulate", "Mô phỏng");
    }
}
