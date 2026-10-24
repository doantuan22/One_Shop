package com.oneshop;

import com.oneshop.dto.request.UpdateAccountRequest;
import com.oneshop.service.AccountProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;

class QaFixProfileDatabaseIntegrationTest extends V3RegressionTestSupport {
    @Autowired AccountProfileService profiles;

    @Test void validProfilePatchIgnoresForgedSensitiveFields() throws Exception {
        var before=jdbc.queryForMap("select * from dbo.users where email=?",ALICE);
        try {
            var response=postAs(ALICE,"/account","fullName=QA+Profile&phone=0987654321&role=ADMIN&status=INACTIVE"
                    +"&email=intruder%40example.com&userName=intruder&passwordHash=forged",true);
            assertThat(response.statusCode()).isEqualTo(302);
            var after=jdbc.queryForMap("select * from dbo.users where email=?",ALICE);
            assertThat(after.get("full_name")).isEqualTo("QA Profile");
            assertThat(after.get("phone")).isEqualTo("0987654321");
            for(String field:java.util.List.of("role_id","status","email","user_name","password_hash"))
                assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
        } finally {
            jdbc.update("update dbo.users set full_name=?,phone=?,updated_at=? where email=?",
                    before.get("full_name"),before.get("phone"),before.get("updated_at"),ALICE);
        }
    }

    @Test void inactiveAccountCannotUpdateEvenWithPreviouslyAuthenticatedServiceContext() {
        var before=jdbc.queryForMap("select full_name,phone from dbo.users where email=?",ALICE);
        jdbc.update("update dbo.users set status='INACTIVE' where email=?",ALICE);
        assertThatThrownBy(()->customer(ALICE,()->{profiles.update(ALICE,new UpdateAccountRequest("Wrong","0912345678"));return null;}))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForMap("select full_name,phone from dbo.users where email=?",ALICE)).isEqualTo(before);
    }
}
