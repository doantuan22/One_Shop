package com.oneshop;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Fast, database-free regression checks for the V3 customer checkout's consent and submit controls. */
class CheckoutTemplateContractTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/checkout/index.html");

    @Test
    void anUnconfirmedCodSplitCannotBeSubmittedFromTheInitialPage() throws Exception {
        String html = Files.readString(TEMPLATE, StandardCharsets.UTF_8);

        assertThat(html).contains("th:disabled=\"${preview.plan == null or preview.splitConsentRequired}\"");
        assertThat(html).contains("consent.addEventListener(\"change\", syncSubmitAvailability)");
        assertThat(html).contains("submit.disabled = !isValidPlan || Boolean(consent && !consent.checked)");
        assertThat(html).contains("splitCodConsentHelp").contains("os-order-split-amounts");
    }

    @Test
    void changingAnAllocationChoiceRefreshesPreviewWithGetAndCannotAccidentallyOrderByEnter() throws Exception {
        String html = Files.readString(TEMPLATE, StandardCharsets.UTF_8);

        assertThat(html).contains("type=\"button\" class=\"btn btn-outline-secondary btn-sm\"");
        assertThat(html).contains("refreshButton.addEventListener(\"click\", refreshPreview)");
        assertThat(html).contains("form.getAttribute(\"data-refresh-url\") + \"?\" + params.toString()");
        assertThat(html).contains("isValidPlan = Boolean(checkoutPlan && checkoutPlan.value)");
        assertThat(html).contains("event.submitter.id === \"refresh-plan\"");
    }

    @Test
    void finalOrderSubmissionHasVisibleSingleSubmitFeedbackAndAStockFailureAlert() throws Exception {
        String html = Files.readString(TEMPLATE, StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"checkout-error\"", "role=\"alert\"");
        assertThat(html).contains("id=\"checkout-submitting\"", "aria-live=\"polite\"");
        assertThat(html).contains("submit.disabled = true");
        assertThat(html).contains("submit.setAttribute(\"aria-busy\", \"true\")");
    }

    @Test
    void onlinePaymentIsClearlyIdentifiedAsSimulatedBeforeTheCustomerChoosesIt() throws Exception {
        String checkout = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        String result = Files.readString(Path.of("src/main/resources/templates/checkout/result.html"), StandardCharsets.UTF_8);

        assertThat(checkout).contains("Thanh toán trực tuyến");
        assertThat(result).contains("Tiến hành thanh toán trực tuyến");
    }
}