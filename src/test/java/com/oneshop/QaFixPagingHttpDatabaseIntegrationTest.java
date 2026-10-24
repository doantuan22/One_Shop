package com.oneshop;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class QaFixPagingHttpDatabaseIntegrationTest extends V3RegressionTestSupport {
    @TestFactory Stream<DynamicTest> everyReaderRejectsOffsetsConsistentlyWithAndWithoutKeyword() {
        List<String[]> inputs=new ArrayList<>();
        for(String route:List.of("/products","/admin/products","/admin/users","/admin/stores","/admin/store-products",
                "/admin/inventory","/admin/orders","/admin/reviews","/admin/staff-assignments","/admin/payments","/admin/stores",
                "/staff/stock","/staff/inventory-history","/staff/fulfillments","/staff/reviews")) {
            int size=route.equals("/products")?12:20;
            int last=Integer.MAX_VALUE/size;
            for(int page:new int[]{-1,0,1,last-1,last,last+1,Integer.MAX_VALUE-1,Integer.MAX_VALUE})
                for(String keyword:List.of("","&q=qa-no-match-999"))inputs.add(new String[]{route,"page="+page+keyword,
                        String.valueOf(page>=0&&(long)page*size>Integer.MAX_VALUE?400:200)});
        }
        return inputs.stream().map(i->DynamicTest.dynamicTest(i[0]+"?"+i[1],()->{
            String actor=i[0].startsWith("/admin")?ADMIN:i[0].startsWith("/staff")?"staff.thuduc@oneshop.vn":null;
            var response=getAs(actor,i[0]+"?"+i[1]);int expected=Integer.parseInt(i[2]);
            var dir=QaAuditEvidence.directory("paging");java.nio.file.Files.createDirectories(dir);
            new com.fasterxml.jackson.databind.ObjectMapper().writeValue(dir.resolve(Integer.toUnsignedString((i[0]+i[1]).hashCode())+".json").toFile(),
                    Map.of("request",i[0]+"?"+i[1],"expected",expected,"actual",response.statusCode(),"timestamp",java.time.Instant.now().toString()));
            assertThat(response.statusCode()).isEqualTo(expected);
        }));
    }
}
