package com.oneshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneshop.dto.request.AdminUserRequest;
import com.oneshop.entity.*;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Correctness assertions intentionally fail until product defects are fixed. Real HTTP/JPA/SQL, test-only barriers. */
class QaAuditRaceDatabaseIntegrationTest extends V3RegressionTestSupport {
    @MockitoSpyBean UserRepository auditUsers;
    @MockitoSpyBean ProductImageRepository auditImages;
    @MockitoSpyBean com.oneshop.repository.ProductRepository auditProducts;
    @Autowired AdminUserService accountAdmin;
    @Autowired com.oneshop.service.ProductService productService;
    private Map<String,Object> savedUser;
    private List<Map<String,Object>> savedImages;

    @AfterEach void restoreAuditRows() {
        reset(auditUsers, auditImages, auditProducts);
        if (savedUser != null) {
            jdbc.update("update dbo.users set role_id=?,status=?,full_name=?,phone=?,updated_at=? where user_id=?",
                    savedUser.get("role_id"), savedUser.get("status"), savedUser.get("full_name"),
                    savedUser.get("phone"), savedUser.get("updated_at"), savedUser.get("user_id"));
        }
        if (savedImages != null) {
            jdbc.update("delete from dbo.product_images where public_id like 'qa-audit-race-%'");
            for (var row : savedImages) jdbc.update("update dbo.product_images set is_primary=? where image_id=?",
                    row.get("is_primary"), row.get("image_id"));
        }
    }

    private void evidence(String name, Object value) throws Exception {
        Path dir=QaAuditEvidence.directory("races"); Files.createDirectories(dir);
        new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                .writeValue(dir.resolve(name+"-"+System.nanoTime()+".json").toFile(),value);
    }

    @RepeatedTest(3) void profileMustNotReactivateAnAccountDisabledDuringRequest() throws Exception {
        profileRace(false);
    }

    @RepeatedTest(3) void profileMustNotRestoreAStaffRoleRevokedDuringRequest() throws Exception {
        profileRace(true);
    }

    private void profileRace(boolean revokeRole) throws Exception {
        String email=STAFF_THUDUC;
        savedUser=jdbc.queryForMap("select user_id,role_id,status,full_name,phone,updated_at from dbo.users where email=?",email);
        long id=((Number)savedUser.get("user_id")).longValue();
        // Same original interleaving at the new persistence boundary, without relying on the removed User merge.
        CountDownLatch atSave=new CountDownLatch(1), resume=new CountDownLatch(1);
        doAnswer(invocation->{
            atSave.countDown();
            if(!resume.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("audit barrier timeout");
            return mockingDetails(auditUsers).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(auditUsers).updateProfileFields(eq(email),anyString(),any(),any());
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<java.net.http.HttpResponse<String>> request=pool.submit(()->postAs(email,"/account",
                    "fullName=QA+Concurrent+Profile&phone=0912345678",true));
            assertThat(atSave.await(20,TimeUnit.SECONDS)).as("authenticated profile reached its write boundary").isTrue();
            admin(()->accountAdmin.update(id,new AdminUserRequest(email,"staff_thuduc",savedUser.get("full_name").toString(),
                    savedUser.get("phone").toString(),revokeRole?RoleName.CUSTOMER:RoleName.STAFF,
                    revokeRole?ActiveStatus.ACTIVE:ActiveStatus.INACTIVE,null)));
            var afterAdmin=jdbc.queryForMap("select role_id,status from dbo.users where user_id=?",id);
            resume.countDown();
            var response=request.get(30,TimeUnit.SECONDS);
            var afterProfile=jdbc.queryForMap("select role_id,status from dbo.users where user_id=?",id);
            evidence(revokeRole?"account-role":"account-status",Map.of("before",savedUser,"afterAdmin",afterAdmin,
                    "afterProfile",afterProfile,"httpStatus",response.statusCode(),
                    "nextStaffHttpStatus",getAs(email,"/staff/dashboard").statusCode()));
            assertThat(response.statusCode()).isIn(302,409,403);
            assertThat(afterProfile).as("profile must preserve security changes committed by Admin").isEqualTo(afterAdmin);
            assertThat(getAs(email,"/staff/dashboard").statusCode()).isIn(302,403);
        } finally {resume.countDown();pool.shutdownNow();pool.awaitTermination(35,TimeUnit.SECONDS);}
    }

    @RepeatedTest(3) void parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary() throws Exception {
        long product=data.productId(SERUM);
        savedImages=jdbc.queryForList("select image_id,is_primary from dbo.product_images where product_id=?",product);
        assertThat(savedImages.stream().filter(r->Boolean.TRUE.equals(r.get("is_primary"))).count()).isEqualTo(1);
        for(int i=0;i<2;i++) jdbc.update("insert into dbo.product_images(product_id,image_url,public_id,is_primary,sort_order) values(?,?,?,0,?)",
                product,"https://example.invalid/audit.png","qa-audit-race-"+i,90+i);
        List<Long> ids=jdbc.queryForList("select image_id from dbo.product_images where product_id=? and public_id like 'qa-audit-race-%' order by image_id",Long.class,product);
        // Barrier at lock acquisition, not after reading the rows: forcing two reads while holding the same
        // exclusive lock would deadlock the harness, not test the fixed serialization protocol.
        CountDownLatch bothEntered=new CountDownLatch(2);
        doAnswer(invocation->{
            bothEntered.countDown();
            if(!bothEntered.await(20,TimeUnit.SECONDS))throw new IllegalStateException("images entry barrier timeout");
            return mockingDetails(auditProducts).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(auditProducts).findByIdForUpdate(product);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=pool.submit(()->productService.setPrimaryProductImage(product,ids.get(0)));
            Future<?> second=pool.submit(()->productService.setPrimaryProductImage(product,ids.get(1)));
            first.get(30,TimeUnit.SECONDS);second.get(30,TimeUnit.SECONDS);
            var after=jdbc.queryForList("select image_id,is_primary from dbo.product_images where product_id=? order by image_id",product);
            evidence("primary-image",Map.of("before",savedImages,"after",after,"bothTransactionsCommitted",true));
            assertThat(after.stream().filter(r->Boolean.TRUE.equals(r.get("is_primary"))).count())
                    .as("exactly one primary per Product after overlapping Admin selections").isEqualTo(1);
        } finally {pool.shutdownNow();pool.awaitTermination(35,TimeUnit.SECONDS);}
    }
}
