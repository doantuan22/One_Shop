package com.oneshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.ZoneId;

/**
 * payOS credentials and limits. The three keys come only from the server environment ({@code PAYOS_CLIENT_ID},
 * {@code PAYOS_API_KEY}, {@code PAYOS_CHECKSUM_KEY}); they are never sent to a browser, logged or committed.
 */
@ConfigurationProperties(prefix = "oneshop.payos")
public class PayosProperties {

    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";

    /** Public https origin of OneShop, e.g. {@code https://shop.example.com}; payOS redirects and calls back here. */
    private String publicBaseUrl = "";

    /** Override of the payOS API origin (tests only). Empty = the SDK default {@code https://api-merchant.payos.vn}. */
    private String apiBaseUrl = "";

    /** The hosted checkout page of a link is {@code <checkoutBaseUrl><paymentLinkId>}; used only to recover a link. */
    private String checkoutBaseUrl = "https://pay.payos.vn/web/";

    private int timeoutMs = 15000;
    private int maxRetries = 1;

    /** Smallest / largest amount (VND) OneShop will ask payOS for. payOS publishes no limit; the bank and payOS enforce theirs. */
    private long minAmount = 2000;
    private long maxAmount = 500_000_000L;

    /** Zone of {@code transactionDateTime} values payOS sends (Vietnam local time). */
    private String bankZone = "Asia/Ho_Chi_Minh";

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey) && validPublicBaseUrl();
    }

    /** Human-readable reason the configuration is unusable, or {@code null} when it is complete. */
    public String problem() {
        if (!notBlank(clientId) || !notBlank(apiKey) || !notBlank(checksumKey)) {
            return "PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY chưa được cấu hình";
        }
        if (!validPublicBaseUrl()) {
            return "ONESHOP_PUBLIC_BASE_URL phải là địa chỉ https công khai của OneShop (không có đường dẫn)";
        }
        return null;
    }

    private boolean validPublicBaseUrl() {
        try {
            URI uri = URI.create(publicBaseUrl.trim());
            String host = uri.getHost();
            if (host == null || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                return false;
            }
            boolean local = "localhost".equals(host) || "127.0.0.1".equals(host);
            return "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Origin without a trailing slash. */
    public String origin() {
        String url = publicBaseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public ZoneId bankZoneId() {
        return ZoneId.of(bankZone);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getChecksumKey() { return checksumKey; }
    public void setChecksumKey(String checksumKey) { this.checksumKey = checksumKey == null ? "" : checksumKey; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl; }
    public String getCheckoutBaseUrl() { return checkoutBaseUrl; }
    public void setCheckoutBaseUrl(String checkoutBaseUrl) { this.checkoutBaseUrl = checkoutBaseUrl; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getMinAmount() { return minAmount; }
    public void setMinAmount(long minAmount) { this.minAmount = minAmount; }
    public long getMaxAmount() { return maxAmount; }
    public void setMaxAmount(long maxAmount) { this.maxAmount = maxAmount; }
    public String getBankZone() { return bankZone; }
    public void setBankZone(String bankZone) { this.bankZone = bankZone; }
}
