package com.oneshop;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/** Current HTTP readers, technical integer/date/enum limits, no unsafe destructive input. */
class QaAuditBoundaryDatabaseIntegrationTest extends V3RegressionTestSupport {
    @TestFactory Stream<DynamicTest> readerParameterBoundaries() {
        List<String[]> cases=new ArrayList<>();
        for(String route:List.of("/products","/admin/products","/admin/users","/admin/stores","/admin/store-products",
                "/admin/inventory","/admin/orders","/admin/reviews","/admin/staff-assignments","/admin/payments",
                "/staff/stock","/staff/inventory-history","/staff/fulfillments","/staff/reviews")) {
            for(String page:List.of("-1","0","1","107374181","107374182","107374183",
                    "178956969","178956970","178956971","2147483646","2147483647","2147483648","abc","1.5"))
                for(String q:List.of("","&q=qa-no-match-999")) cases.add(new String[]{route,"page="+page+q});
        }
        for(String route:List.of("/staff/fulfillments","/staff/inventory-history","/admin/orders","/admin/payments"))
            for(String params:List.of("from=0001-01-01&to=9999-12-31","from=999999999-12-31&to=999999999-12-31",
                    "from=invalid&to=invalid","from=2026-10-25&to=2026-10-24","status=INVALID","storeId=9223372036854775808"))
                cases.add(new String[]{route,params});
        return cases.stream().map(input->DynamicTest.dynamicTest(input[0]+"?"+input[1],()->{
            String path=input[0]+"?"+input[1];String actor=path.startsWith("/admin")?"admin@oneshop.vn":
                    path.startsWith("/staff")?"staff.thuduc@oneshop.vn":null;
            var response=getAs(actor,path);
            Path dir=QaAuditEvidence.directory("boundaries");Files.createDirectories(dir);
            String key=Integer.toUnsignedString(path.hashCode());
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(dir.resolve(key+".json").toFile(),
                    Map.of("request",path,"actual",response.statusCode(),"expected","success/empty page or controlled 4xx; never 5xx",
                            "actor",actor==null?"guest":actor,"timestamp",java.time.Instant.now().toString()));
            assertThat(response.statusCode()).as(path).isBetween(200,499);
        }));
    }
}
