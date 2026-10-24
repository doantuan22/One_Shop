package com.oneshop;

import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.service.CloudinaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL rollback; only the external storage boundary is replaced by a deterministic in-memory file ledger. */
class QaAuditImageRollbackDatabaseIntegrationTest extends V3RegressionTestSupport {
    @MockitoBean CloudinaryService externalImages;
    @org.springframework.beans.factory.annotation.Autowired com.oneshop.service.ProductService productService;
    private Map<String,Object> brandBefore;

    @AfterEach void restoreBrand() {
        if(brandBefore!=null) jdbc.update("update dbo.brands set logo_url=?,logo_public_id=? where brand_id=?",
                brandBefore.get("logo_url"),brandBefore.get("logo_public_id"),brandBefore.get("brand_id"));
    }

    private Set<String> storage(String old) {
        Set<String> files=ConcurrentHashMap.newKeySet();if(old!=null)files.add(old);
        when(externalImages.uploadImage(any(),anyString())).thenAnswer(i->{files.add("qa-audit/new");return new ImageUploadResult("https://example.invalid/new.png","qa-audit/new");});
        doAnswer(i->{files.remove(i.getArgument(0));return null;}).when(externalImages).deleteImage(anyString());
        return files;
    }
    private MockMultipartFile file(){return new MockMultipartFile("file","test.png","image/png",new byte[]{1});}

    @RepeatedTest(2) void uploadedProductFileMustBeCompensatedWhenTransactionRollsBack() throws Exception {
        var files=storage(null);long id=data.productId(SERUM);
        int before=count("select count(*) from dbo.product_images where product_id=?",id);
        tx.executeWithoutResult(s->{
            productService.addProductImage(id,file());
            // A failure at the final transaction boundary, after the service's successful flush.
            s.setRollbackOnly();
        });
        assertThat(count("select count(*) from dbo.product_images where product_id=?",id)).isEqualTo(before);
        saveEvidence("product-orphan",Map.of("databaseRowsBefore",before,"databaseRowsAfter",before,"externalFilesAfterRollback",files));
        assertThat(files).as("rolled-back upload must not leak an unreferenced external file").isEmpty();
    }

    @RepeatedTest(2) void replacingBrandImageMustNotDeleteOldFileBeforeCommit() throws Exception {
        brandBefore=jdbc.queryForMap("select brand_id,logo_url,logo_public_id from dbo.brands where brand_id=1");
        jdbc.update("update dbo.brands set logo_url='https://example.invalid/old.png',logo_public_id='qa-audit/old' where brand_id=1");
        var files=storage("qa-audit/old");
        tx.executeWithoutResult(s->{productService.uploadBrandLogo(1L,file());s.setRollbackOnly();});
        String reference=jdbc.queryForObject("select logo_public_id from dbo.brands where brand_id=1",String.class);
        assertThat(reference).isEqualTo("qa-audit/old");
        saveEvidence("brand-dangling",Map.of("databaseReferenceAfterRollback",reference,"externalFilesAfterRollback",files));
        assertThat(files).as("a rollback must keep the file still referenced by the database").contains(reference);
    }

    private void saveEvidence(String name,Object data)throws Exception {
        var dir=QaAuditEvidence.directory("image-rollback");java.nio.file.Files.createDirectories(dir);
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(dir.resolve(name+"-"+System.nanoTime()+".json").toFile(),data);
    }
}
