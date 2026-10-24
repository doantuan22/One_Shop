package com.oneshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real-product seed (database/02_seed.sql) against its evidence (docs/seed/products.json): every product in the
 * database has a name/price/size that matches the sourced manifest, at least one https source URL, a verification date,
 * a Cloudinary image of the OneShop namespace and an image credit; demo reviews are marked as simulated data.
 * Read only: it never writes, so it can run on any database built from the two official scripts.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class RealProductSeedIntegrityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode manifest() throws Exception {
        return MAPPER.readTree(Files.readString(Path.of("docs/seed/products.json")));
    }

    @Test
    @DisplayName("SEED-01 every product is a manifest product: same SKU, name and price, nothing extra, nothing missing")
    void databaseMatchesTheSourcedManifest() throws Exception {
        JsonNode products = manifest().get("products");
        Set<String> manifestSkus = new HashSet<>();
        for (JsonNode p : products) {
            manifestSkus.add(p.get("sku").asText());
            var rows = jdbc.queryForList("select name, price, original_price from dbo.products where sku = ?", p.get("sku").asText());
            assertThat(rows).as(p.get("sku").asText()).hasSize(1);
            assertThat((String) rows.get(0).get("name")).isEqualTo(p.get("name").asText());
            assertThat((BigDecimal) rows.get(0).get("price")).isEqualByComparingTo(BigDecimal.valueOf(p.get("price").asLong()));
            assertThat(rows.get(0).get("original_price")).as("original_price is left empty (listing prices only)").isNull();
        }
        Set<String> dbSkus = new HashSet<>(jdbc.queryForList("select sku from dbo.products", String.class));
        assertThat(dbSkus).isEqualTo(manifestSkus);
    }

    @Test
    @DisplayName("SEED-02 every product carries evidence: https source URLs, a verification date, a match grade and a price that a source supports")
    void everyProductHasEvidence() throws Exception {
        JsonNode doc = manifest();
        assertThat(doc.get("verified_on").asText()).isEqualTo("2026-10-20");
        for (JsonNode p : doc.get("products")) {
            String sku = p.get("sku").asText();
            assertThat(p.get("verified_on").asText()).as(sku).isEqualTo("2026-10-20");
            assertThat(p.get("grade").asText()).as(sku).isIn("A", "B");
            assertThat(p.get("sources")).as(sku).isNotEmpty();
            List<Long> observed = new ArrayList<>();
            for (JsonNode s : p.get("sources")) {
                assertThat(s.get("url").asText()).as(sku).startsWith("https://");
                if (!s.get("observed_price_vnd").isNull()) {
                    observed.add(s.get("observed_price_vnd").asLong());
                }
            }
            // the chosen listing price is what at least one source showed, or the line's list price documented in the note
            boolean supported = observed.contains(p.get("price").asLong())
                    || p.get("sources").toString().contains(String.format("%,d", p.get("price").asLong()).replace(',', '.'));
            assertThat(supported).as(sku + " price " + p.get("price").asLong() + " vs " + observed).isTrue();
        }
    }

    @Test
    @DisplayName("SEED-03 every product has exactly one primary image in the OneShop Cloudinary namespace, credited under CC BY-SA 3.0")
    void everyProductImageIsOurOwnCreditedAsset() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from dbo.products p where (select count(*) from dbo.product_images i "
                + "where i.product_id = p.product_id and i.is_primary = 1) <> 1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.product_images where image_url not like 'https://res.cloudinary.com/%' "
                + "or public_id not like 'oneshop/v3/products/%' or image_url not like '%' + public_id + '%'", Integer.class)).isZero();
        JsonNode credits = MAPPER.readTree(Files.readString(Path.of("src/main/resources/data/image-credits.json")));
        assertThat(credits.get("license").asText()).isEqualTo("CC BY-SA 3.0");
        Set<String> credited = new HashSet<>();
        for (JsonNode item : credits.get("items")) {
            credited.add(item.get("sku").asText());
            assertThat(item.get("author").asText()).as(item.get("sku").asText()).isNotBlank();
            assertThat(item.get("obfPage").asText()).startsWith("https://world.openbeautyfacts.org/product/");
        }
        assertThat(credited).isEqualTo(new HashSet<>(jdbc.queryForList("select sku from dbo.products", String.class)));
        for (JsonNode p : manifest().get("products")) {
            String publicId = jdbc.queryForObject("select i.public_id from dbo.product_images i join dbo.products pr on pr.product_id = i.product_id "
                    + "where pr.sku = ?", String.class, p.get("sku").asText());
            assertThat(publicId).isEqualTo(p.get("image").get("cloudinary_public_id").asText());
        }
    }

    @Test
    @DisplayName("SEED-04 simulated data is marked as such: demo reviews, Mock-gateway transaction codes, no external gateway reference")
    void simulatedDataIsLabelled() {
        assertThat(jdbc.queryForObject("select count(*) from dbo.reviews where comment not like N'[[]Dữ liệu mô phỏng]%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.review_replies where content not like N'[[]Dữ liệu mô phỏng]%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where transaction_code is not null and transaction_code not like 'MOCK-SEED-%'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dbo.users u join dbo.roles r on r.role_id = u.role_id where r.name = 'CUSTOMER' "
                + "and u.email not like '%@example.com'", Integer.class)).as("demo customers use example.com").isZero();
    }

    @Test
    @DisplayName("SEED-05 catalog shape for the UI: several brands and categories, stocked, sold-out, discontinued and not-yet-sold products, multi-Store stock")
    void catalogIsVariedEnoughForTheScreens() {
        assertThat(jdbc.queryForObject("select count(*) from dbo.brands", Integer.class)).isGreaterThanOrEqualTo(6);
        assertThat(jdbc.queryForObject("select count(distinct category_id) from dbo.products where status = 'ACTIVE'", Integer.class)).isGreaterThanOrEqualTo(4);
        assertThat(jdbc.queryForObject("select count(distinct price) from dbo.products where status = 'ACTIVE'", Integer.class)).isGreaterThanOrEqualTo(10);
        assertThat(jdbc.queryForObject("select min(price) from dbo.products where status = 'ACTIVE'", BigDecimal.class)).isLessThan(BigDecimal.valueOf(100000));
        assertThat(jdbc.queryForObject("select max(price) from dbo.products where status = 'ACTIVE'", BigDecimal.class)).isGreaterThan(BigDecimal.valueOf(500000));
        assertThat(jdbc.queryForObject("select count(*) from dbo.products where status = 'INACTIVE'", Integer.class)).as("discontinued").isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.products p where p.status = 'ACTIVE' and not exists "
                + "(select 1 from dbo.store_products sp where sp.product_id = p.product_id)", Integer.class)).as("not sold anywhere yet").isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.products p where p.status = 'ACTIVE' and exists (select 1 from dbo.store_products sp "
                + "where sp.product_id = p.product_id) and not exists (select 1 from dbo.store_products sp where sp.product_id = p.product_id and sp.quantity > 0)",
                Integer.class)).as("sold out chain-wide").isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from (select product_id from dbo.store_products where quantity > 0 and status = 'ACTIVE' "
                + "group by product_id having count(*) >= 3) t", Integer.class)).as("stock at 3+ Stores").isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from dbo.store_products where quantity between 1 and 5", Integer.class)).as("low stock").isGreaterThanOrEqualTo(1);
    }
}
