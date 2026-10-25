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
@org.junit.jupiter.api.Tag("live")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "CLOUDINARY_LIVE_TEST", matches = "true")
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
        String uploadedPublicId=null;
        try {
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
            var scenario=new Phase9IntegrationScenario(jdbc,"http://localhost:"+port);
            String cookie=scenario.account("admin@example.com");
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
            uploadedPublicId=publicId;
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
            assertThat(deletion.headers().firstValue("Location").orElse("")).endsWith("success=image-deleted");image=null;uploadedPublicId=null;
            assertThat(jdbc.queryForObject("select count(*) from dbo.product_images where product_id=?",Integer.class,product)).isZero();
            assertThatThrownBy(()->cloudinary.api().resource(publicId,ObjectUtils.asMap("resource_type","image")))
                .as("Provider confirms newly created asset was deleted").hasMessageContaining("not found");
        } finally {
            try {
                if(image!=null) {
                    try { products.deleteProductImage(product,image); } catch(Exception ignored) {}
                }
                if(uploadedPublicId!=null) {
                    try { cloudinary.uploader().destroy(uploadedPublicId, ObjectUtils.emptyMap()); } catch(Exception ignored) {}
                }
            }
            finally { jdbc.update("delete from dbo.products where product_id=? and sku=?",product,request.getSku()); }
            assertThat(jdbc.queryForList("select * from dbo.products order by product_id")).isEqualTo(originalProducts);
            assertThat(jdbc.queryForList("select * from dbo.product_images order by image_id")).isEqualTo(originalImages);
        }
    }

    @Test void tc18LiveBrandLogoUploadReplaceDeleteWithRealJpegAndWebp() throws Exception {
        assertThat(properties.isConfigured()).as("Live credentials required").isTrue();
        var originalBrands = jdbc.queryForList("select * from dbo.brands order by brand_id");
        var scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port);
        String cookie = scenario.account("admin@example.com");

        var brandReq = new com.oneshop.dto.request.BrandRequest();
        brandReq.setName("PH12-LIVE-BRAND-" + UUID.randomUUID().toString().substring(0, 8));
        brandReq.setDescription("Live brand test");
        long brandId = products.createBrand(brandReq).id();
        String initialPublicId = null;
        String replacedPublicId = null;

        try {
            // Real JPEG image upload
            byte[] jpegBytes = new byte[] {
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                    0x01, 0x01, 0x00, 0x60, 0x00, 0x60, 0x00, 0x00, (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08,
                    0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
                    0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C,
                    0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30,
                    0x31, 0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32, (byte) 0xFF,
                    (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xC4,
                    0x00, 0x1F, 0x00, 0x00, 0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, (byte) 0xFF,
                    (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, (byte) 0xBF, (byte) 0xFF, (byte) 0xD9
            };
            var uploadJpeg = sendMultipart(scenario, cookie, "/admin/brands/" + brandId + "/logo", "file", "logo.jpg", "image/jpeg", jpegBytes);
            assertThat(uploadJpeg.statusCode()).isEqualTo(302);
            assertThat(uploadJpeg.headers().firstValue("Location").orElse("")).endsWith("success=logo-uploaded");

            var row1 = jdbc.queryForMap("select logo_url, logo_public_id from dbo.brands where brand_id=?", brandId);
            initialPublicId = (String) row1.get("logo_public_id");
            assertThat(initialPublicId).isNotBlank();
            assertThat((String) row1.get("logo_url")).startsWith("https://res.cloudinary.com/");

            // Replace with real WebP image
            byte[] webpBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/main/resources/static/images/site/categories/category-skincare.webp"));
            var uploadWebp = sendMultipart(scenario, cookie, "/admin/brands/" + brandId + "/logo", "file", "logo.webp", "image/webp", webpBytes);
            assertThat(uploadWebp.statusCode()).isEqualTo(302);
            assertThat(uploadWebp.headers().firstValue("Location").orElse("")).endsWith("success=logo-uploaded");

            var row2 = jdbc.queryForMap("select logo_url, logo_public_id from dbo.brands where brand_id=?", brandId);
            replacedPublicId = (String) row2.get("logo_public_id");
            assertThat(replacedPublicId).isNotBlank().isNotEqualTo(initialPublicId);

            // Provider confirms previous image was destroyed
            final String oldId = initialPublicId;
            assertThatThrownBy(() -> cloudinary.api().resource(oldId, ObjectUtils.asMap("resource_type", "image")))
                    .hasMessageContaining("not found");

            // Provider confirms new WebP image exists
            var liveWebp = cloudinary.api().resource(replacedPublicId, ObjectUtils.asMap("resource_type", "image"));
            assertThat(liveWebp.get("secure_url")).isEqualTo(row2.get("logo_url"));

            // Remove logo
            var removeResp = scenario.post(cookie, "/admin/brands/" + brandId + "/logo/delete", "");
            assertThat(removeResp.statusCode()).isEqualTo(302);
            assertThat(removeResp.headers().firstValue("Location").orElse("")).endsWith("success=logo-removed");

            var row3 = jdbc.queryForMap("select logo_url, logo_public_id from dbo.brands where brand_id=?", brandId);
            assertThat(row3.get("logo_url")).isNull();
            assertThat(row3.get("logo_public_id")).isNull();

            // Provider confirms replaced image was also deleted
            final String finalId = replacedPublicId;
            assertThatThrownBy(() -> cloudinary.api().resource(finalId, ObjectUtils.asMap("resource_type", "image")))
                    .hasMessageContaining("not found");
            replacedPublicId = null;
        } finally {
            try {
                if (initialPublicId != null) {
                    try { cloudinary.uploader().destroy(initialPublicId, ObjectUtils.emptyMap()); } catch (Exception ignored) {}
                }
                if (replacedPublicId != null) {
                    try { cloudinary.uploader().destroy(replacedPublicId, ObjectUtils.emptyMap()); } catch (Exception ignored) {}
                }
            } finally {
                jdbc.update("delete from dbo.brands where brand_id=?", brandId);
            }
            assertThat(jdbc.queryForList("select * from dbo.brands order by brand_id")).isEqualTo(originalBrands);
        }
    }

    @Test void tc19LiveUploadRejectsInvalidFileAndOversizedFile() throws Exception {
        assertThat(properties.isConfigured()).as("Live credentials required").isTrue();
        var scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port);
        String cookie = scenario.account("admin@example.com");

        // SVG file disguised as PNG -> rejected
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"10\"/></svg>".getBytes(StandardCharsets.UTF_8);
        var svgResp = sendMultipart(scenario, cookie, "/admin/categories/1/image", "file", "fake.png", "image/png", svgBytes);
        assertThat(svgResp.statusCode()).isEqualTo(302);
        assertThat(svgResp.headers().firstValue("Location").orElse("")).contains("error=image-invalid");

        // Oversized file (> 5MB) -> rejected with error=image-size
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1024];
        byte[] validPngPrefix = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        System.arraycopy(validPngPrefix, 0, largeBytes, 0, validPngPrefix.length);
        var largeResp = sendMultipart(scenario, cookie, "/admin/categories/1/image", "file", "oversized.png", "image/png", largeBytes);
        assertThat(largeResp.statusCode()).isEqualTo(302);
        assertThat(largeResp.headers().firstValue("Location").orElse("")).contains("error=image-size");
    }

    private HttpResponse<String> sendMultipart(Phase9IntegrationScenario scenario, String cookie, String targetUrl,
                                               String fieldName, String filename, String contentType, byte[] data) throws Exception {
        String editPage = targetUrl.replaceFirst("/(images|logo|image)$", "/edit");
        var page = scenario.get(editPage, cookie);
        var csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        String boundary = "phase12-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf.group(1)
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + targetUrl))
                .header("Cookie", cookie + "; " + csrfCookie)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
}
