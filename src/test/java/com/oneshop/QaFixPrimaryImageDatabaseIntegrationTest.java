package com.oneshop;

import com.oneshop.service.CloudinaryService;
import com.oneshop.service.ProductService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QaFixPrimaryImageDatabaseIntegrationTest extends V3RegressionTestSupport {
    @MockitoBean CloudinaryService provider;
    @Autowired ProductService catalog;
    List<Map<String,Object>> beforeImages;
    long maxImage,product;
    @BeforeEach void rememberImages() {
        product=data.productId(SERUM);
        beforeImages=jdbc.queryForList("select image_id,is_primary from dbo.product_images where product_id=?",product);
        maxImage=jdbc.queryForObject("select max(image_id) from dbo.product_images",Long.class);
        when(provider.uploadImage(any(),anyString())).thenAnswer(i->new com.oneshop.dto.response.ImageUploadResult("https://example.invalid/test.png","qa-primary/"+UUID.randomUUID()));
    }
    @AfterEach void restoreImages() {
        jdbc.update("delete from dbo.product_images where image_id>?",maxImage);
        jdbc.update("update dbo.product_images set is_primary=0 where product_id=?",product);
        for(var image:beforeImages)jdbc.update("update dbo.product_images set is_primary=? where image_id=?",image.get("is_primary"),image.get("image_id"));
    }
    long add(){return catalog.addProductImage(product,new MockMultipartFile("file","a.png","image/png",new byte[]{1})).id();}
    long primary(){return jdbc.queryForObject("select image_id from dbo.product_images where product_id=? and is_primary=1",Long.class,product);}
    @RepeatedTest(5) void sixIndependentTransactionsSelectPrimaryWithoutDeadlockOrDuplicate()throws Exception {
        List<Long> ids=new ArrayList<>();for(int i=0;i<6;i++)ids.add(add());
        CountDownLatch go=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> commits=new ArrayList<>();
            for(long id:ids)commits.add(pool.submit(()->{go.await();catalog.setPrimaryProductImage(product,id);return null;}));
            go.countDown();for(var commit:commits)commit.get(40,TimeUnit.SECONDS);
            assertThat(ids).contains(primary());
            assertThat(count("select count(*) from dbo.product_images where product_id=? and is_primary=1",product)).isEqualTo(1);
            var dir=QaAuditEvidence.directory("primary");java.nio.file.Files.createDirectories(dir);
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(dir.resolve("six-writers-"+System.nanoTime()+".json").toFile(),
                    Map.of("writers",6,"commits",6,"primary",primary(),"rows",jdbc.queryForList("select image_id,is_primary from dbo.product_images where product_id=?",product),"timestamp",java.time.Instant.now().toString()));
        } finally {pool.shutdownNow();}
    }
    @Test void repeatedSelectionAndDeletePrimaryPromoteOneRemainingImage() {
        long image=add();catalog.setPrimaryProductImage(product,image);catalog.setPrimaryProductImage(product,image);
        assertThat(primary()).isEqualTo(image);catalog.deleteProductImage(product,image);
        assertThat(primary()).isEqualTo(((Number)beforeImages.getFirst().get("image_id")).longValue());
    }
    @Test void otherProductsAndMissingImagesCannotBeSelected() {
        long old=primary();long other=jdbc.queryForObject("select top 1 image_id from dbo.product_images where product_id<>?",Long.class,product);
        assertThatThrownBy(()->catalog.setPrimaryProductImage(product,other)).isInstanceOf(com.oneshop.exception.ResourceNotFoundException.class);
        assertThatThrownBy(()->catalog.setPrimaryProductImage(product,Long.MAX_VALUE)).isInstanceOf(com.oneshop.exception.ResourceNotFoundException.class);
        assertThat(primary()).isEqualTo(old);
    }
    @Test void filteredUniqueIndexRejectsDuplicateEvenOutsideService() {
        assertThat(count("select count(*) from sys.indexes where object_id=object_id('dbo.product_images') and name='UX_product_images_one_primary' and is_unique=1 and has_filter=1 and is_disabled=0")).isEqualTo(1);
        long old=primary();
        assertThatThrownBy(()->jdbc.update("insert into dbo.product_images(product_id,image_url,public_id,is_primary,sort_order) values(?,?,?,1,999)",product,"https://example.invalid/no.png","qa-primary/duplicate"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(primary()).isEqualTo(old);
    }
    @Test void actualAdminHttpActionChecksOwnershipAndSelectsPrimary()throws Exception {
        long id=add();
        assertThat(postAs(ADMIN,"/admin/products/"+product+"/images/"+id+"/primary","",true).statusCode()).isEqualTo(302);
        assertThat(primary()).isEqualTo(id);
        assertThat(postAs(ALICE,"/admin/products/"+product+"/images/"+id+"/primary","",true).statusCode()).isEqualTo(403);
    }
}
