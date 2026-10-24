package com.oneshop;

import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The licence obligation: the attribution page is public, shows source, author and licence, and is reachable from every page. */
class ImageCreditsPageIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void stubProducts() {
        ProductResponse product = new ProductResponse(1L, "SKU-001", "Kem dưỡng ẩm", "Mô tả", "Dưỡng da", "OneBrand", null);
        when(storeProductService.getChainCatalog(any(), any())).thenReturn(new PageImpl<>(List.of(
                new CatalogItemResponse(product, null, List.of()))));
    }

    @Test
    void theCreditsPageIsPublicAndNamesSourceAuthorAndLicence() throws Exception {
        HttpResponse<String> response = get("/image-credits");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();
        assertThat(html).contains("data-layout=\"client\"", "Ghi công hình ảnh sản phẩm", "Open Beauty Facts", "CC BY-SA 3.0",
                "https://creativecommons.org/licenses/by-sa/3.0/deed.en", "không chỉnh sửa nội dung sản phẩm trong ảnh", "id=\"image-credits-table\"");
        assertThat(html).contains("Simple Purifying Gel Wash 150ml", "thaialagata",
                "https://world.openbeautyfacts.org/product/");
        // one row per credited product
        assertThat(html.split("<tr>", -1).length - 1).as("header + 27 products").isGreaterThanOrEqualTo(28);
    }

    @Test
    void everyPageLinksToTheCredits() throws Exception {
        assertThat(get("/").body()).contains("href=\"/image-credits\"", "Ghi công hình ảnh");
        assertThat(get("/products").body()).contains("href=\"/image-credits\"");
    }
}
