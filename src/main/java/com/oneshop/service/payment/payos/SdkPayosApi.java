package com.oneshop.service.payment.payos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneshop.config.PayosProperties;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.exception.APIException;
import vn.payos.exception.ConnectionException;
import vn.payos.exception.ConnectionTimeoutException;
import vn.payos.exception.NotFoundException;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.WebhookData;

import java.util.List;
import java.util.Map;

/**
 * {@link PayosApi} over the official payOS Java SDK ({@code vn.payos:payos-java}). The SDK signs requests with the
 * checksum key and verifies webhook signatures; the client is built lazily so an unconfigured server still starts.
 */
@Component
public class SdkPayosApi implements PayosApi {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayosProperties properties;
    private volatile PayOS client;

    public SdkPayosApi(PayosProperties properties) {
        this.properties = properties;
    }

    private PayOS client() {
        PayOS local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    if (!notBlank(properties.getClientId()) || !notBlank(properties.getApiKey())
                            || !notBlank(properties.getChecksumKey())) {
                        throw new PayosException.NotConfigured("payOS chưa được cấu hình trên máy chủ.");
                    }
                    ClientOptions.ClientOptionsBuilder builder = ClientOptions.builder()
                            .clientId(properties.getClientId())
                            .apiKey(properties.getApiKey())
                            .checksumKey(properties.getChecksumKey())
                            .timeoutMs(properties.getTimeoutMs())
                            .maxRetries(properties.getMaxRetries());
                    if (notBlank(properties.getApiBaseUrl())) {
                        builder.baseURL(properties.getApiBaseUrl());
                    }
                    client = local = new PayOS(builder.build());
                }
            }
        }
        return local;
    }

    @Override
    public LinkCreated createLink(LinkRequest request) {
        CreatePaymentLinkRequest sdk = CreatePaymentLinkRequest.builder()
                .orderCode(request.orderCode())
                .amount(request.amount())
                .description(request.description())
                .returnUrl(request.returnUrl())
                .cancelUrl(request.cancelUrl())
                .expiredAt(request.expiredAtEpochSeconds())
                .item(PaymentLinkItem.builder().name(request.itemName()).quantity(1).price(request.amount()).build())
                .build();
        CreatePaymentLinkResponse response = call(() -> client().paymentRequests().create(sdk));
        return new LinkCreated(response.getPaymentLinkId(), response.getCheckoutUrl(),
                response.getOrderCode() == null ? request.orderCode() : response.getOrderCode(),
                response.getAmount() == null ? request.amount() : response.getAmount(),
                response.getStatus() == null ? null : response.getStatus().name());
    }

    @Override
    public LinkInfo getLink(long orderCode) {
        return map(call(() -> client().paymentRequests().get(Long.valueOf(orderCode))));
    }

    @Override
    public LinkInfo cancelLink(long orderCode, String reason) {
        return map(call(() -> client().paymentRequests().cancel(Long.valueOf(orderCode), reason)));
    }

    @Override
    public WebhookEvent verifyWebhook(String rawBody) {
        try {
            Map<String, Object> body = JSON.readValue(rawBody, new TypeReference<Map<String, Object>>() { });
            // the SDK checks the HMAC-SHA256 signature of `data` with the checksum key
            WebhookData data = client().webhooks().verify(body);
            Boolean success = body.get("success") instanceof Boolean b ? b : null;
            String code = body.get("code") == null ? null : String.valueOf(body.get("code"));
            return new WebhookEvent(Boolean.TRUE.equals(success), code, data.getOrderCode(), data.getAmount(),
                    data.getCurrency(), data.getPaymentLinkId(), data.getReference(), data.getTransactionDateTime(),
                    data.getCode());
        } catch (PayosException ex) {
            throw ex;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            // verification is an offline computation: whatever stops it (bad JSON, no data, no/wrong signature) means
            // the body cannot be trusted
            throw new PayosException.InvalidSignature("Webhook payOS không hợp lệ", ex);
        }
    }

    @Override
    public void confirmWebhook(String webhookUrl) {
        call(() -> client().webhooks().confirm(webhookUrl));
    }

    // ------------------------------------------------------------------ helpers

    private static LinkInfo map(PaymentLink link) {
        List<Transaction> transactions = link.getTransactions() == null ? List.of()
                : link.getTransactions().stream()
                .map(t -> new Transaction(t.getReference(), t.getAmount() == null ? 0 : t.getAmount(),
                        t.getTransactionDateTime())).toList();
        return new LinkInfo(link.getId(), link.getOrderCode() == null ? 0 : link.getOrderCode(),
                link.getAmount() == null ? 0 : link.getAmount(), link.getAmountPaid() == null ? 0 : link.getAmountPaid(),
                link.getAmountRemaining() == null ? 0 : link.getAmountRemaining(),
                link.getStatus() == null ? null : link.getStatus().name(), transactions);
    }

    /** Classifies SDK failures: a timeout / network / 5xx is UNKNOWN, a 4xx is a definite refusal. */
    private static <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (PayosException ex) {
            throw ex;
        } catch (NotFoundException ex) {
            throw new PayosException.NotFound("Không tìm thấy link thanh toán payOS", ex);
        } catch (ConnectionTimeoutException | ConnectionException ex) {
            throw new PayosException.Unavailable("Không kết nối được payOS", ex);
        } catch (APIException ex) {
            int status = ex.getStatusCode().orElse(0);
            if (status >= 500 || status == 429 || status == 0) {
                throw new PayosException.Unavailable("payOS tạm thời không phản hồi (" + status + ")", ex);
            }
            throw new PayosException.Rejected("payOS từ chối yêu cầu (" + status + " "
                    + ex.getErrorCode().orElse("") + ")", ex);
        } catch (PayOSException ex) {
            throw new PayosException.Unavailable("Lỗi khi gọi payOS", ex);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
