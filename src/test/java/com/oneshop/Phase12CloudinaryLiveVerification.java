package com.oneshop;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.oneshop.config.CloudinaryProperties;
import com.oneshop.dto.request.ProductRequest;
import com.oneshop.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Explicit live verification, deliberately outside Surefire's default *Test pattern.
 * Run: mvn -Dtest=Phase12CloudinaryLiveVerification test
 * No mocks, assumptions or skips: requires configured SQL Server and real Cloudinary credentials/network.
 * Uploads only its new fixture and removes it; never replaces an existing product image.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
class Phase12CloudinaryLiveVerification {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductService products;
    @Autowired CloudinaryProperties properties;
    @Autowired Cloudinary cloudinary;
    @LocalServerPort int port;

    @Test void tc17LiveUploadStoresOnlyUrlAndPublicIdAndRemovesOnlyItsFixture() throws Exception {
        assertThat(properties.isConfigured()).as("Live credentials required; this test must not skip").isTrue();
        var originalProducts=jdbc.queryForList("select * from dbo.products order by product_id");
        var originalImages=jdbc.queryForList("select * from dbo.product_images order by image_id");
        ProductRequest request=new ProductRequest();
        request.setCategoryId(jdbc.queryForObject("select top 1 category_id from dbo.categories order by category_id",Long.class));
        request.setBrandId(jdbc.queryForObject("select top 1 brand_id from dbo.brands order by brand_id",Long.class));
        request.setSku("PH12-LIVE-"+UUID.randomUUID().toString().substring(0,16));
        request.setName("Phase12 live verification fixture");
        long product=products.createProduct(request).id();
        Long image=null;
        try {
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
            var scenario=new Phase9IntegrationScenario(jdbc,"http://localhost:"+port);
            String cookie=scenario.account("admin@oneshop.vn");
            var page=scenario.get("/admin/products/"+product+"/edit",cookie);
            assertThat(page.statusCode()).isEqualTo(200);
            var csrf=Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());assertThat(csrf.find()).isTrue();
            String csrfCookie=page.headers().allValues("Set-Cookie").stream().filter(c->c.startsWith("XSRF-TOKEN="))
                .map(c->c.split(";")[0]).findFirst().orElseThrow();
            String boundary="phase12-"+UUID.randomUUID();var body=new ByteArrayOutputStream();
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n"+csrf.group(1)
                +"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"phase12-live.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(png);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var upload=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/admin/products/"+product+"/images"))
                .header("Cookie",cookie+"; "+csrfCookie).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(upload.statusCode()).isEqualTo(302);
            assertThat(upload.headers().firstValue("Location").orElse("")).endsWith("success=image-uploaded");
            image=jdbc.queryForObject("select image_id from dbo.product_images where product_id=?",Long.class,product);
            var row=jdbc.queryForMap("select * from dbo.product_images where image_id=?",image);
            String url=(String)row.get("image_url"),publicId=(String)row.get("public_id");
            assertThat(url).startsWith("https://res.cloudinary.com/");
            assertThat(publicId).isNotBlank();
            assertThat(products.getProductImages(product)).singleElement().satisfies(dto->assertThat(dto.imageUrl()).isEqualTo(url));
            var live=cloudinary.api().resource(publicId,ObjectUtils.asMap("resource_type","image"));
            assertThat(live.get("secure_url")).isEqualTo(url);
            assertThat(live.get("resource_type")).isEqualTo("image");
            assertThat(((Number)live.get("bytes")).longValue()).isGreaterThan(0);
            var delivered=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                .send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(delivered.statusCode()).isEqualTo(200);
            assertThat(delivered.headers().firstValue("Content-Type").orElse("")).startsWith("image/");
            assertThat(delivered.body()).isNotEmpty();
            assertThat(jdbc.queryForObject("select count(*) from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME='product_images' and DATA_TYPE in ('binary','varbinary','image')",Integer.class)).isZero();
            var deletion=scenario.post(cookie,"/admin/products/"+product+"/images/"+image+"/delete","");
            assertThat(deletion.statusCode()).isEqualTo(302);
            assertThat(deletion.headers().firstValue("Location").orElse("")).endsWith("success=image-deleted");image=null;
            assertThat(jdbc.queryForObject("select count(*) from dbo.product_images where product_id=?",Integer.class,product)).isZero();
            assertThatThrownBy(()->cloudinary.api().resource(publicId,ObjectUtils.asMap("resource_type","image")))
                .as("Provider confirms newly created asset was deleted").hasMessageContaining("not found");
        } finally {
            try { if(image!=null)products.deleteProductImage(product,image); }
            finally { jdbc.update("delete from dbo.products where product_id=? and sku=?",product,request.getSku()); }
            assertThat(jdbc.queryForList("select * from dbo.products order by product_id")).isEqualTo(originalProducts);
            assertThat(jdbc.queryForList("select * from dbo.product_images order by image_id")).isEqualTo(originalImages);
        }
    }
}
