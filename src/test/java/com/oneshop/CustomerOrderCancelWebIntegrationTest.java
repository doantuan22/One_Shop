package com.oneshop;

import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.payment.CustomerOrderCancellationService.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The cancel redirect tells the customer when the order is cancelled but its refund still has to be retried. */
class CustomerOrderCancelWebIntegrationTest extends AbstractIntegrationTest {
    private static final String EMAIL = "customer@oneshop.test";
    @MockitoBean private CustomerOrderService orders;
    @Autowired private JwtService jwt;
    @Autowired private CustomUserDetailsService users;

    private String cookie() {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash("x");
        user.setRole(new Role(RoleName.CUSTOMER));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        withId(user);
        return "ONESHOP_TOKEN=" + jwt.generateToken(users.loadUserByUsername(EMAIL));
    }

    private HttpResponse<String> cancel(String cookie) throws Exception {
        var page = get("/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post("/orders/10/cancel", "application/x-www-form-urlencoded", "_csrf=" + csrf.group(1),
                "Cookie", cookie + "; " + tokenCookie);
    }

    private String location(Result result) throws Exception {
        when(orders.cancelOrder(EMAIL, 10L)).thenReturn(result);
        var response = cancel(cookie());
        assertThat(response.statusCode()).isEqualTo(302);
        return response.headers().firstValue("Location").orElseThrow();
    }

    @Test
    void aCancelledOrderRedirectsToItsPage() throws Exception {
        assertThat(location(Result.CANCELLED)).endsWith("/orders/10?cancelled");
        assertThat(location(Result.CANCELLED_AND_REFUNDED)).endsWith("/orders/10?cancelled");
        assertThat(location(Result.ALREADY_CANCELLED)).endsWith("/orders/10?cancelled");
    }

    @Test
    void aFailedRefundSimulationIsReportedToTheCustomer() throws Exception {
        assertThat(location(Result.CANCELLED_REFUND_FAILED)).endsWith("/orders/10?cancelled&refund=pending");
    }
}
