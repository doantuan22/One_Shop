package com.oneshop;

import com.oneshop.service.CloudinaryService;
import org.junit.jupiter.api.*;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import(QaFixUiBrowserDatabaseIntegrationTest.TitlePage.class)
class QaFixUiBrowserDatabaseIntegrationTest extends V3RegressionTestSupport {
    @TestConfiguration(proxyBeanMethods=false) @Controller
    static class TitlePage {
        @GetMapping("/products/qa-title") String title(@RequestParam String text,org.springframework.ui.Model model) {
            model.addAttribute("title",text);return "qa/title";
        }
    }
    @MockitoBean CloudinaryService provider;
    Map<String,Object> userBefore;
    List<Map<String,Object>> imagesBefore;
    long product,maxImage;
    Set<String> files;
    @BeforeEach void rememberUiFixture() {
        product=data.productId(SERUM);
        imagesBefore=jdbc.queryForList("select image_id,is_primary from dbo.product_images where product_id=?",product);
        maxImage=jdbc.queryForObject("select max(image_id) from dbo.product_images",Long.class);
        userBefore=jdbc.queryForMap("select full_name,phone,updated_at,role_id,status from dbo.users where email=?",ALICE);
        files=ConcurrentHashMap.newKeySet();
        when(provider.uploadImage(any(),anyString())).thenAnswer(i->{String id="qa-fix-ui/"+UUID.randomUUID();files.add(id);
            return new com.oneshop.dto.response.ImageUploadResult("/images/site/categories/category-placeholder.svg",id);});
        doAnswer(i->{files.remove(i.getArgument(0));return null;}).when(provider).deleteImage(anyString());
    }
    @AfterEach void restoreUiFixture() {
        jdbc.update("delete from dbo.product_images where image_id>?",maxImage);
        jdbc.update("update dbo.product_images set is_primary=0 where product_id=?",product);
        for(var image:imagesBefore)jdbc.update("update dbo.product_images set is_primary=? where image_id=?",image.get("is_primary"),image.get("image_id"));
        jdbc.update("update dbo.users set full_name=?,phone=?,updated_at=? where email=?",userBefore.get("full_name"),userBefore.get("phone"),userBefore.get("updated_at"),ALICE);
        jdbc.update("delete from dbo.image_cleanup_tasks where public_id like 'qa-fix-ui/%'");
    }
    void browser(String mode)throws Exception {
        Path dir=QaAuditEvidence.directory("ui-"+mode).toAbsolutePath();Files.createDirectories(dir);
        Path config=dir.resolve("config.json");var json=new com.fasterxml.jackson.databind.ObjectMapper();
        json.writeValue(config.toFile(),Map.of("port",port,"mode",mode,"output",dir.toString(),"productId",product,
                "chrome","C:/Program Files/Google/Chrome/Application/chrome.exe"));
        var process=new ProcessBuilder("node","scripts/qa-audit/fix-ui.mjs",config.toString()).redirectErrorStream(true)
                .redirectOutput(dir.resolve("node.log").toFile()).start();
        try {assertThat(process.waitFor(240,TimeUnit.SECONDS)).as("Chrome finished: "+dir).isTrue();
            assertThat(process.exitValue()).as("see "+dir+"/results.json").isZero();}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
    @Test void seedResourcesAndFallback()throws Exception {
        var seeded=jdbc.queryForMap("select image_url,image_public_id from dbo.categories where name=N'Trang điểm'");
        String url=(String)seeded.get("image_url");
        assertThat(url).isNotBlank();
        if(url.startsWith("/images/site/categories/"))assertThat(seeded.get("image_public_id")).isNull();
        else assertThat(url).doesNotContain("trang-diem.webp");
        browser("resources");
    }
    @Test void realFormsPrimaryFallbackProfileAndSafeTitles()throws Exception {
        browser("forms-titles");
        assertThat(jdbc.queryForMap("select full_name,phone,role_id,status from dbo.users where email=?",ALICE))
                .containsEntry("full_name","QA UI Profile").containsEntry("phone","0987654321")
                .containsEntry("role_id",userBefore.get("role_id")).containsEntry("status",userBefore.get("status"));
        assertThat(files).as("uploaded test file deleted after the real UI delete commits").isEmpty();
        assertThat(jdbc.queryForList("select image_id,is_primary from dbo.product_images where product_id=?",product)).isEqualTo(imagesBefore);
    }

    @Test void existingRoleNavigationAndModalChecksStillPass()throws Exception {
        Path dir=QaAuditEvidence.directory("ui-full-screen").toAbsolutePath();Files.createDirectories(dir);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();Path config=dir.resolve("config.json");
        json.writeValue(config.toFile(),Map.of("port",port,"output",dir.toString(),"chrome","C:/Program Files/Google/Chrome/Application/chrome.exe"));
        var process=new ProcessBuilder("node","scripts/qa-audit/screens.mjs",config.toString()).redirectErrorStream(true)
                .redirectOutput(dir.resolve("node.log").toFile()).start();
        try {assertThat(process.waitFor(240,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).as("see "+dir+"/results.json").isZero();}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
}
