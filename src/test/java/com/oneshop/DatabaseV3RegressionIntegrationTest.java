package com.oneshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit read integrity and the already approved Review-only migration on the isolated *_test database. */
class DatabaseV3RegressionIntegrationTest extends V3RegressionTestSupport {
    @Value("${spring.jpa.hibernate.ddl-auto}") String ddl;

    @Test void all23TablesHaveTrustedConstraintsAndHibernateOnlyValidates(){
        assertThat(ddl).isEqualTo("validate");
        assertThat(count("select count(*) from sys.tables where is_ms_shipped=0")).isEqualTo(29);
        assertThat(count("select count(*) from sys.foreign_keys where is_disabled=1 or is_not_trusted=1")).isZero();
        assertThat(count("select count(*) from sys.check_constraints where is_disabled=1 or is_not_trusted=1")).isZero();
        Integer violations=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) connection->{
            int rows=0;
            try(var statement=connection.createStatement()){
                boolean result=statement.execute("DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS, NO_INFOMSGS");
                while(true){
                    if(result){try(var set=statement.getResultSet()){while(set.next())rows++;}}
                    else if(statement.getUpdateCount()==-1)break;
                    result=statement.getMoreResults();
                }
            }
            return rows;
        });
        assertThat(violations).isZero();
    }

    @Test void noOrphansInvalidSourcesNegativeStockOrRepeatedRestoration(){
        for(String sql:List.of(
                "select count(*) from dbo.order_items i left join dbo.order_fulfillments f on f.fulfillment_id=i.fulfillment_id left join dbo.store_products sp on sp.store_product_id=i.store_product_id where f.fulfillment_id is null or sp.store_product_id is null or f.order_id<>i.order_id or f.store_id<>sp.store_id",
                "select count(*) from dbo.reviews r left join dbo.order_items i on i.order_item_id=r.order_item_id where r.order_item_id is not null and (i.order_item_id is null or i.order_id<>r.order_id)",
                "select count(*) from dbo.review_replies p left join dbo.reviews r on r.review_id=p.review_id where r.review_id is null",
                "select count(*) from dbo.payments p left join dbo.orders o on o.order_id=p.order_id where o.order_id is null",
                "select count(*) from dbo.inventory_movements m left join dbo.store_products p on p.store_product_id=m.store_product_id where p.store_product_id is null or m.quantity_after<>m.quantity_before+m.quantity_change",
                "select count(*) from dbo.store_products where quantity<0 or reserved_quantity<0 or reserved_quantity>quantity",
                "select count(*) from (select reference_order_id,store_product_id from dbo.inventory_movements where type='CANCEL_ORDER' group by reference_order_id,store_product_id having count(*)>1) d"))
            assertThat(count(sql)).as(sql).isZero();
    }

    @Test void approvedReviewMigrationIsIdempotentAndPreservesEveryTable()throws Exception{
        assertThat(jdbc.queryForObject("select db_name()",String.class)).endsWith("_test");
        var before=tableChecksums();
        String migration=Files.readString(Path.of("docs/v3/migration-history/20261014_be06_review_uniqueness.sql"));
        jdbc.execute(migration);jdbc.execute(migration);
        assertThat(tableChecksums()).isEqualTo(before);
        assertThat(count("select count(*) from sys.indexes where object_id=object_id('dbo.reviews') and name in ('UX_reviews_order_item','UX_reviews_legacy_user_product') and is_unique=1 and has_filter=1 and is_disabled=0")).isEqualTo(2);
        assertThat(count("select count(*) from sys.key_constraints where parent_object_id=object_id('dbo.reviews') and name='UQ_reviews_user_product'")).isZero();
    }

    private Map<String,List<Map<String,Object>>> tableChecksums(){
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:jdbc.queryForList("select name from sys.tables where is_ms_shipped=0",String.class))
            result.put(table,jdbc.queryForList("select count_big(*) n,checksum_agg(binary_checksum(*)) c from dbo.["+table+"]"));
        return result;
    }
}
