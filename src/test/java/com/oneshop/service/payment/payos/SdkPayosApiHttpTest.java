package com.oneshop.service.payment.payos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneshop.config.PayosProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real payOS SDK against a local HTTP server that plays payOS: request shape and signature, response mapping,
 * error classification (refused vs. unknown) and webhook signature verification. No network, no real keys.
 */
class SdkPayosApiHttpTest {

    private static final String CHECKSUM = "test-checksum-key-0123456789";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private PayosProperties properties;
    private SdkPayosApi api;
    private PayOS signer;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger createStatus = new AtomicInteger(200);
    private volatile int delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + " x-client-id=" + exchange.getRequestHeaders().getFirst("x-client-id")
                    + " x-api-key=" + exchange.getRequestHeaders().getFirst("x-api-key"));
            bodies.add(body);
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            Map<String, Object> envelope = new LinkedHashMap<>();
            if (exchange.getRequestMethod().equals("POST") && path.equals("/v2/payment-requests")) {
                status = createStatus.get();
                if (status == 200) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("bin", "970422");
                    data.put("accountNumber", "0123456789");
                    data.put("accountName", "OneShop");
                    data.put("amount", 200000);
                    data.put("description", "OneShop 7");
                    data.put("orderCode", 123456789);
                    data.put("currency", "VND");
                    data.put("paymentLinkId", "link-abc");
                    data.put("status", "PENDING");
                    data.put("checkoutUrl", "https://pay.payos.vn/web/link-abc");
                    data.put("qrCode", "qr");
                    envelope.put("code", "00");
                    envelope.put("desc", "success");
                    envelope.put("data", data);
                    envelope.put("signature", sign(data));
                } else {
                    envelope.put("code", "231");
                    envelope.put("desc", "order code already exists");
                    envelope.put("data", null);
                }
            } else if (exchange.getRequestMethod().equals("GET") && path.startsWith("/v2/payment-requests/")) {
                if (path.endsWith("/404")) {
                    status = 404;
                    envelope.put("code", "101");
                    envelope.put("desc", "not found");
                    envelope.put("data", null);
                } else {
                    Map<String, Object> tx = new LinkedHashMap<>();
                    tx.put("reference", "FT26300001");
                    tx.put("amount", 200000);
                    tx.put("accountNumber", "0123456789");
                    tx.put("description", "OneShop 7");
                    tx.put("transactionDateTime", "2026-10-27 16:05:00");
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("id", "link-abc");
                    data.put("orderCode", 123456789);
                    data.put("amount", 200000);
                    data.put("amountPaid", 200000);
                    data.put("amountRemaining", 0);
                    data.put("status", "PAID");
                    data.put("createdAt", "2026-10-27T16:00:00+07:00");
                    data.put("transactions", List.of(tx));
                    envelope.put("code", "00");
                    envelope.put("desc", "success");
                    envelope.put("data", data);
                    envelope.put("signature", sign(data)); // the SDK verifies the response signature too
                }
            }
            byte[] out = JSON.writeValueAsBytes(envelope);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();

        properties = new PayosProperties();
        properties.setClientId("client-id-1");
        properties.setApiKey("api-key-1");
        properties.setChecksumKey(CHECKSUM);
        properties.setPublicBaseUrl("https://shop.example.com");
        properties.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setMaxRetries(0);
        properties.setTimeoutMs(1500);
        api = new SdkPayosApi(properties);
        signer = new PayOS("client-id-1", "api-key-1", CHECKSUM);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String sign(Map<String, Object> data) {
        return signer.getCrypto().createSignatureFromObj(data, CHECKSUM);
    }

    private static PayosApi.LinkRequest request() {
        return new PayosApi.LinkRequest(123456789L, 200000L, "OneShop 7", "Don hang OneShop #7",
                "https://shop.example.com/orders/7/payments/payos/return",
                "https://shop.example.com/orders/7/payments/payos/cancel", 1_900_000_000L);
    }

    @Test
    void createLinkSendsCredentialsAndASignedBodyAndMapsTheResponse() throws Exception {
        PayosApi.LinkCreated created = api.createLink(request());
        assertThat(created.paymentLinkId()).isEqualTo("link-abc");
        assertThat(created.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/link-abc");
        assertThat(created.orderCode()).isEqualTo(123456789L);
        assertThat(requests.get(0)).startsWith("POST /v2/payment-requests")
                .contains("x-client-id=client-id-1").contains("x-api-key=api-key-1");
        Map<?, ?> sent = JSON.readValue(bodies.get(0), Map.class);
        assertThat(((Number) sent.get("amount")).longValue()).isEqualTo(200000L);
        assertThat(((Number) sent.get("orderCode")).longValue()).isEqualTo(123456789L);
        assertThat(sent.get("returnUrl")).isEqualTo("https://shop.example.com/orders/7/payments/payos/return");
        assertThat(((Number) sent.get("expiredAt")).longValue()).isEqualTo(1_900_000_000L);
        // HMAC-SHA256 over the five documented fields, sorted, with the checksum key
        String expected = signer.getCrypto().createSignatureFromObj(new LinkedHashMap<>(Map.of(
                "amount", 200000, "cancelUrl", "https://shop.example.com/orders/7/payments/payos/cancel",
                "description", "OneShop 7", "orderCode", 123456789,
                "returnUrl", "https://shop.example.com/orders/7/payments/payos/return")), CHECKSUM);
        assertThat(sent.get("signature")).isEqualTo(expected);
        // the secret never travels in the body
        assertThat(bodies.get(0)).doesNotContain(CHECKSUM).doesNotContain("api-key-1");
    }

    @Test
    void aDefiniteRefusalIsRejectedNotUnknown() {
        createStatus.set(400);
        assertThatThrownBy(() -> api.createLink(request())).isInstanceOf(PayosException.Rejected.class);
    }

    @Test
    void serverErrorAndTimeoutAreUnknownOutcomes() {
        createStatus.set(502);
        assertThatThrownBy(() -> api.createLink(request())).isInstanceOf(PayosException.Unavailable.class);
        createStatus.set(200);
        delayMs = 3000; // longer than the 1500 ms client timeout
        assertThatThrownBy(() -> api.createLink(request())).isInstanceOf(PayosException.Unavailable.class);
    }

    @Test
    void getLinkMapsAmountsStatusAndTransactions() {
        PayosApi.LinkInfo info = api.getLink(123456789L);
        assertThat(info.status()).isEqualTo("PAID");
        assertThat(info.amount()).isEqualTo(200000L);
        assertThat(info.amountPaid()).isEqualTo(200000L);
        assertThat(info.transactions()).singleElement().satisfies(t -> {
            assertThat(t.reference()).isEqualTo("FT26300001");
            assertThat(t.transactionDateTime()).isEqualTo("2026-10-27 16:05:00");
        });
        assertThat(requests.get(0)).startsWith("GET /v2/payment-requests/123456789");
    }

    @Test
    void unknownLinkIsNotFound() {
        assertThatThrownBy(() -> api.getLink(404L)).isInstanceOf(PayosException.NotFound.class);
    }

    @Test
    void unconfiguredServerRefusesWithoutCallingPayos() {
        SdkPayosApi unconfigured = new SdkPayosApi(new PayosProperties());
        assertThatThrownBy(() -> unconfigured.getLink(1L)).isInstanceOf(PayosException.NotConfigured.class);
        assertThat(requests).isEmpty();
    }

    // ------------------------------------------------------------------ webhook signature

    private String webhook(Map<String, Object> data, String signature) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "00");
        body.put("desc", "success");
        body.put("success", true);
        body.put("data", data);
        body.put("signature", signature);
        return JSON.writeValueAsString(body);
    }

    private static Map<String, Object> paymentData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123456789);
        data.put("amount", 200000);
        data.put("description", "OneShop 7");
        data.put("accountNumber", "0123456789");
        data.put("reference", "FT26300001");
        data.put("transactionDateTime", "2026-10-27 16:05:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "link-abc");
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    @Test
    void validWebhookSignatureIsVerifiedAndMapped() throws Exception {
        Map<String, Object> data = paymentData();
        PayosApi.WebhookEvent event = api.verifyWebhook(webhook(data, sign(data)));
        assertThat(event.success()).isTrue();
        assertThat(event.orderCode()).isEqualTo(123456789L);
        assertThat(event.amount()).isEqualTo(200000L);
        assertThat(event.paymentLinkId()).isEqualTo("link-abc");
        assertThat(event.reference()).isEqualTo("FT26300001");
        assertThat(event.dataCode()).isEqualTo("00");
        assertThat(event.currency()).isEqualTo("VND");
    }

    @Test
    void tamperedAmountFailsTheSignature() throws Exception {
        Map<String, Object> data = paymentData();
        String signature = sign(data);
        data.put("amount", 2000); // an attacker lowers... or raises the amount after signing
        assertThatThrownBy(() -> api.verifyWebhook(webhook(data, signature)))
                .isInstanceOf(PayosException.InvalidSignature.class);
    }

    @Test
    void wrongKeyGarbageAndMissingSignatureAreRejected() throws Exception {
        Map<String, Object> data = paymentData();
        String foreign = new PayOS("a", "b", "another-checksum-key").getCrypto()
                .createSignatureFromObj(data, "another-checksum-key");
        assertThatThrownBy(() -> api.verifyWebhook(webhook(data, foreign))).isInstanceOf(PayosException.InvalidSignature.class);
        assertThatThrownBy(() -> api.verifyWebhook(webhook(data, ""))).isInstanceOf(PayosException.InvalidSignature.class);
        assertThatThrownBy(() -> api.verifyWebhook("not json")).isInstanceOf(PayosException.InvalidSignature.class);
        assertThatThrownBy(() -> api.verifyWebhook("{}")).isInstanceOf(PayosException.InvalidSignature.class);
    }

    @Test
    void anUnsignedSampleLikePayosUrlConfirmationIsNotTrustedWithoutAValidSignature() throws Exception {
        // data copied from the payOS documentation sample, but signed by someone who does not know our checksum key
        Map<String, Object> sample = paymentData();
        sample.put("orderCode", 123);
        sample.put("amount", 3000);
        String bad = new ArrayList<>(List.of("0000")).get(0);
        assertThatThrownBy(() -> api.verifyWebhook(webhook(sample, bad))).isInstanceOf(PayosException.InvalidSignature.class);
    }
}
