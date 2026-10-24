package com.oneshop;

import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.service.CloudinaryService;
import com.oneshop.service.ProductService;
import com.oneshop.service.image.ImageCleanupService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QaFixImageLifecycleDatabaseIntegrationTest extends V3RegressionTestSupport {
    enum Target { PRODUCT, BRAND, CATEGORY }
    @MockitoBean CloudinaryService provider;
    @Autowired ProductService catalog;
    @Autowired ImageCleanupService cleanup;
    @MockitoSpyBean com.oneshop.repository.ProductImageRepository imageRows;
    Set<String> files;
    AtomicBoolean unavailable;
    AtomicReference<String> uploaded;
    List<Map<String,Object>> brandsBefore,categoriesBefore;
    long lastImage;

    @BeforeEach void storageFixture() {
        files=ConcurrentHashMap.newKeySet();unavailable=new AtomicBoolean();uploaded=new AtomicReference<>();
        brandsBefore=jdbc.queryForList("select brand_id,logo_url,logo_public_id from dbo.brands");
        categoriesBefore=jdbc.queryForList("select category_id,image_url,image_public_id from dbo.categories");
        lastImage=jdbc.queryForObject("select coalesce(max(image_id),0) from dbo.product_images",Long.class);
        when(provider.uploadImage(any(),anyString())).thenAnswer(i->{
            String id="qa-fix/"+UUID.randomUUID();files.add(id);uploaded.set(id);
            return new ImageUploadResult("https://example.invalid/"+id+".png",id);
        });
        doAnswer(i->{if(unavailable.get())throw new com.oneshop.exception.ImageStorageException("simulated provider unavailable");
            files.remove(i.getArgument(0));return null;}).when(provider).deleteImage(anyString());
    }
    @AfterEach void restoreAssets() {
        reset(imageRows);
        jdbc.update("delete from dbo.product_images where image_id>?",lastImage);
        for(var b:brandsBefore)jdbc.update("update dbo.brands set logo_url=?,logo_public_id=? where brand_id=?",b.get("logo_url"),b.get("logo_public_id"),b.get("brand_id"));
        for(var c:categoriesBefore)jdbc.update("update dbo.categories set image_url=?,image_public_id=? where category_id=?",c.get("image_url"),c.get("image_public_id"),c.get("category_id"));
        jdbc.update("delete from dbo.image_cleanup_tasks where public_id like 'qa-fix/%'");
    }
    MockMultipartFile file(){return new MockMultipartFile("file","fixture.png","image/png",new byte[]{1});}
    long product(){return data.productId(SERUM);}
    void upload(Target target) {
        switch(target) {
            case PRODUCT -> catalog.addProductImage(product(),file());
            case BRAND -> catalog.uploadBrandLogo(1L,file());
            case CATEGORY -> catalog.uploadCategoryImage(1L,file());
        }
    }
    String old(Target target) {
        String id="qa-fix/old-"+target;files.add(id);
        switch(target) {
            case PRODUCT -> jdbc.update("insert into dbo.product_images(product_id,image_url,public_id,is_primary,sort_order) values(?,?,?,0,100)",product(),"https://example.invalid/old.png",id);
            case BRAND -> jdbc.update("update dbo.brands set logo_url='https://example.invalid/old.png',logo_public_id=? where brand_id=1",id);
            case CATEGORY -> jdbc.update("update dbo.categories set image_url='https://example.invalid/old.png',image_public_id=? where category_id=1",id);
        }
        return id;
    }
    void delete(Target target,String old) {
        switch(target) {
            case PRODUCT -> catalog.deleteProductImage(product(),jdbc.queryForObject("select image_id from dbo.product_images where public_id=?",Long.class,old));
            case BRAND -> catalog.removeBrandLogo(1L);
            case CATEGORY -> catalog.removeCategoryImage(1L);
        }
    }
    int references(String id) {
        return count("select (select count(*) from dbo.product_images where public_id=?) + (select count(*) from dbo.brands where logo_public_id=?) + (select count(*) from dbo.categories where image_public_id=?)",id,id,id);
    }
    int pending(){return count("select count(*) from dbo.image_cleanup_tasks where public_id like 'qa-fix/%'");}
    void evidence(String scenario)throws Exception {
        var dir=QaAuditEvidence.directory("image-lifecycle");java.nio.file.Files.createDirectories(dir);
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                dir.resolve(scenario+"-"+System.nanoTime()+".json").toFile(),Map.of("scenario",scenario,"files",files,
                        "tasks",jdbc.queryForList("select * from dbo.image_cleanup_tasks where public_id like 'qa-fix/%'"),
                        "timestamp",java.time.Instant.now().toString(),"database",jdbc.queryForObject("select db_name()",String.class)));
    }

    @ParameterizedTest @EnumSource(Target.class) void uploadCommitKeepsReferencedFile(Target target)throws Exception {
        upload(target);assertThat(files).contains(uploaded.get());assertThat(references(uploaded.get())).isPositive();assertThat(pending()).isZero();evidence("upload-commit-"+target);
    }
    @ParameterizedTest @EnumSource(Target.class) void uploadRollbackCompensatesAfterSuccessfulFlush(Target target)throws Exception {
        tx.executeWithoutResult(s->{upload(target);entityManager.flush();s.setRollbackOnly();});
        assertThat(files).doesNotContain(uploaded.get());assertThat(references(uploaded.get())).isZero();assertThat(pending()).isZero();evidence("upload-rollback-"+target);
    }
    @ParameterizedTest @EnumSource(value=Target.class,names={"BRAND","CATEGORY"}) void replaceCommitDeletesOldOnlyAfterCommit(Target target)throws Exception {
        String old=old(target);
        tx.executeWithoutResult(s->{upload(target);assertThat(files).contains(old,uploaded.get());});
        assertThat(files).contains(uploaded.get()).doesNotContain(old);assertThat(references(uploaded.get())).isEqualTo(1);evidence("replace-commit-"+target);
    }
    @ParameterizedTest @EnumSource(value=Target.class,names={"BRAND","CATEGORY"}) void replaceRollbackKeepsOldAndCleansNew(Target target)throws Exception {
        String old=old(target);tx.executeWithoutResult(s->{upload(target);s.setRollbackOnly();});
        assertThat(files).contains(old).doesNotContain(uploaded.get());assertThat(references(old)).isEqualTo(1);evidence("replace-rollback-"+target);
    }
    @ParameterizedTest @EnumSource(Target.class) void deleteCommitDeletesAfterMetadataCommit(Target target)throws Exception {
        String old=old(target);tx.executeWithoutResult(s->{delete(target,old);assertThat(files).contains(old);});
        assertThat(files).doesNotContain(old);assertThat(references(old)).isZero();evidence("delete-commit-"+target);
    }
    @ParameterizedTest @EnumSource(Target.class) void deleteRollbackPreservesMetadataAndFile(Target target)throws Exception {
        String old=old(target);tx.executeWithoutResult(s->{delete(target,old);s.setRollbackOnly();});
        assertThat(files).contains(old);assertThat(references(old)).isEqualTo(1);assertThat(pending()).isZero();evidence("delete-rollback-"+target);
    }
    @Test void failedRollbackCompensationIsJournaledAndRetryIsIdempotent()throws Exception {
        unavailable.set(true);tx.executeWithoutResult(s->{upload(Target.PRODUCT);s.setRollbackOnly();});
        assertThat(files).contains(uploaded.get());assertThat(pending()).isEqualTo(1);
        assertThat(count("select count(*) from dbo.image_cleanup_tasks where attempts=1 and last_error is not null")).isEqualTo(1);
        evidence("cleanup-pending");unavailable.set(false);admin(()->cleanup.retryPending(20));admin(()->cleanup.retryPending(20));
        assertThat(files).doesNotContain(uploaded.get());assertThat(pending()).isZero();evidence("cleanup-retried");
    }
    @Test void failedAfterCommitDeleteDoesNotUndoCommittedMetadata()throws Exception {
        String old=old(Target.BRAND);unavailable.set(true);delete(Target.BRAND,old);
        assertThat(references(old)).isZero();assertThat(files).contains(old);assertThat(pending()).isEqualTo(1);
        unavailable.set(false);admin(()->cleanup.retryPending(20));assertThat(files).doesNotContain(old);assertThat(pending()).isZero();evidence("after-commit-retry");
    }
    @Test void sharedFileIsNeverDeletedWhileAnotherValidRecordReferencesIt()throws Exception {
        String old=old(Target.BRAND);jdbc.update("update dbo.categories set image_url='https://example.invalid/shared.png',image_public_id=? where category_id=1",old);
        delete(Target.BRAND,old);assertThat(files).contains(old);assertThat(references(old)).isEqualTo(1);
        verify(provider,never()).deleteImage(old);delete(Target.CATEGORY,old);assertThat(files).doesNotContain(old);evidence("shared-reference");
    }
    @Test void failedMetadataSaveAfterUploadRollsBackAndCleansNew()throws Exception {
        doThrow(new IllegalStateException("injected metadata write failure")).when(imageRows).saveAndFlush(any());
        assertThatThrownBy(()->upload(Target.PRODUCT)).isInstanceOf(IllegalStateException.class);
        assertThat(references(uploaded.get())).isZero();assertThat(files).doesNotContain(uploaded.get());evidence("metadata-failure");
    }
    @Test void cleanupRetryEndpointRequiresAdminAndCsrf()throws Exception {
        assertThat(postAs(ALICE,"/admin/images/cleanup/retry","",true).statusCode()).isEqualTo(403);
        assertThat(postAs(ADMIN,"/admin/images/cleanup/retry","",false).statusCode()).isEqualTo(403);
        assertThat(postAs(ADMIN,"/admin/images/cleanup/retry","limit=0",true).statusCode()).isEqualTo(400);
        assertThat(postAs(ADMIN,"/admin/images/cleanup/retry","limit=20",true).statusCode()).isEqualTo(302);
    }
}
