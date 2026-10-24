package com.oneshop;

import com.oneshop.service.AdminOperationsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QaFixDateRangeDatabaseIntegrationTest extends V3RegressionTestSupport {
    @Autowired AdminOperationsService adminOrders;
    @ParameterizedTest @ValueSource(strings={"0001-01-01","2026-10-24","9999-12-31"})
    void ordersAndPaymentsIncludeActualEndOfDayRows(String text)throws Exception {
        long cod=cod(),online=online();LocalDate day=LocalDate.parse(text);
        for(long id:List.of(cod,online))jdbc.update("update dbo.orders set created_at=convert(datetime2(0),?,126) where order_id=?",text+"T23:59:59",id);
        jdbc.update("update dbo.payments set created_at=convert(datetime2(0),?,126) where order_id=?",text+"T23:59:59",online);
        for(String q:List.of("", "khachhang1",String.valueOf(online))) {
            var orderRows=admin(()->adminOrders.getOrders(null,q,null,null,null,day,day,0));
            var paymentRows=admin(()->adminPayments.getCases(null,q,day,day,0));
            assertThat(orderRows.getContent()).as("inclusive Orders "+text).extracting(r->r.order().orderId()).contains(online);
            assertThat(paymentRows.getContent()).as("inclusive Payments "+text).extracting(r->r.orderId()).contains(online);
        }
        if(day.getYear()>1) {
            var before=day.minusDays(1);
            assertThat(admin(()->adminOrders.getOrders(null,String.valueOf(online),null,null,null,null,before,0)).getContent()).isEmpty();
            assertThat(admin(()->adminPayments.getCases(null,String.valueOf(online),null,before,0)).getContent()).isEmpty();
        }
        var dir=QaAuditEvidence.directory("dates");java.nio.file.Files.createDirectories(dir);
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(dir.resolve(text+".json").toFile(),Map.of("date",text,
                "createdRows",jdbc.queryForList("select order_id,created_at from dbo.orders where order_id in (?,?)",cod,online),
                "result","end-of-day inclusion verified in Orders and Payments, SQL and text branches","timestamp",java.time.Instant.now().toString()));
    }
    @Test void httpDateRangeValidationIsConsistent()throws Exception {
        for(String route:List.of("/admin/orders","/admin/payments")) {
            for(String q:List.of("","?from=0001-01-01","?to=9999-12-31","?from=0001-01-01&to=9999-12-31","?from=2026-10-24&to=2026-10-24","?from=0001-01-01&to=9999-12-31&attention=true"))
                assertThat(getAs(ADMIN,route+q).statusCode()).as(route+q).isEqualTo(200);
            for(String q:List.of("?from=2026-10-25&to=2026-10-24","?from=invalid","?to=2026-02-30","?to=0000-01-01","?to=%2B10000-01-01"))
                assertThat(getAs(ADMIN,route+q).statusCode()).as(route+q).isEqualTo(400);
        }
    }
}
