package com.oneshop.seed;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browses a database loaded with the LARGE seed (database/rebuild_dev_db.ps1). Runs only when SEED_LARGE_TEST=true;
 * the database is -Dseed.large.db (default: oneshop), never the small-seed test database. Read-only.
 *
 * <pre>
 *   $env:SEED_LARGE_TEST = "true"; mvn test -Dtest=LargeSeedWebIntegrationTest
 * </pre>
 */
@Tag("seed-large")
@EnabledIfEnvironmentVariable(named = "SEED_LARGE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.jwt.secret=test-only-secret-test-only-secret-1234567890", "DB_NAME=${seed.large.db:oneshop}"})
@ActiveProfiles("dev")
class LargeSeedWebIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "text/html").GET();
        if (cookie != null) b.header("Cookie", cookie);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}")).build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(r.body());
        assertThat(m.find()).as("login of " + email).isTrue();
        return "ONESHOP_TOKEN=" + m.group(1);
    }

    @Test
    void catalogCanBeBrowsedAcrossManyPages() throws Exception {
        Integer products = jdbc.queryForObject("select count(*) from dbo.products where status = 'ACTIVE'", Integer.class);
        assertThat(products).isGreaterThan(60);
        HttpResponse<String> first = get("/products?page=0", null);
        HttpResponse<String> third = get("/products?page=2", null);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(third.statusCode()).isEqualTo(200);
        assertThat(third.body()).isNotEqualTo(first.body());
        assertThat(get("/stores", null).statusCode()).isEqualTo(200);
    }

    @Test
    void staffOfTheBusiestStoreSeesMoreThanOnePageOfOrders() throws Exception {
        Integer orders = jdbc.queryForObject("select count(*) from dbo.orders where store_id = 1", Integer.class);
        assertThat(orders).isGreaterThan(40);
        String cookie = login("staff.thuduc@oneshop.vn");
        HttpResponse<String> page0 = get("/staff/orders?page=0", cookie);
        HttpResponse<String> page1 = get("/staff/orders?page=1", cookie);
        assertThat(page0.statusCode()).isEqualTo(200);
        assertThat(page1.statusCode()).isEqualTo(200);
        assertThat(page1.body()).isNotEqualTo(page0.body());
        assertThat(get("/admin", login("admin@oneshop.vn")).statusCode()).isEqualTo(200);
    }

    @Test
    void everySeedImageIsAnHttpsCloudinaryUrl() {
        Integer bad = jdbc.queryForObject("""
                select (select count(*) from dbo.product_images where image_url not like 'https://res.cloudinary.com/%' or public_id is null)
                     + (select count(*) from dbo.brands where logo_url is null or logo_url not like 'https://res.cloudinary.com/%' or logo_public_id is null)
                     + (select count(*) from dbo.categories where image_url is null or image_url not like 'https://res.cloudinary.com/%' or image_public_id is null)
                """, Integer.class);
        assertThat(bad).isZero();
    }
}
