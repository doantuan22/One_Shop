package com.oneshop.config;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.oneshop.config.V3SchemaCompatibilityVerifier.*;

class V3SchemaCompatibilityVerifierTest {
    private List<KeyColumn> valid() {
        return List.of(new KeyColumn("UX_reviews_legacy_user_product",true,false,"([order_item_id] IS NULL)",1,"user_id"),
                new KeyColumn("UX_reviews_legacy_user_product",true,false,"([order_item_id] IS NULL)",2,"product_id"),
                new KeyColumn("UX_reviews_order_item",true,false,"([order_item_id] IS NOT NULL)",1,"order_item_id"));
    }
    @Test void acceptsCanonicalReviewKeys(){assertThatCode(()->requireCompatible(valid())).doesNotThrowAnyException();}
    @Test void refusesMissingMigration(){assertThatThrownBy(()->requireCompatible(List.of())).isInstanceOf(IllegalStateException.class);}
    @Test void refusesRetainedObsoleteGlobalKey(){var keys=new ArrayList<>(valid());keys.add(new KeyColumn("UQ_reviews_user_product",true,false,null,1,"user_id"));assertThatThrownBy(()->requireCompatible(keys)).isInstanceOf(IllegalStateException.class);}
    @Test void refusesWrongFilterAndColumns(){var keys=new ArrayList<>(valid());keys.set(2,new KeyColumn("UX_reviews_order_item",true,false,"order_item_id IS NULL",1,"user_id"));assertThatThrownBy(()->requireCompatible(keys)).isInstanceOf(IllegalStateException.class);}
    @Test void refusesDisabledOrNonUniqueKeys(){for(boolean disabled:List.of(true,false)){var keys=new ArrayList<>(valid());keys.set(2,new KeyColumn("UX_reviews_order_item",false,disabled,"order_item_id IS NOT NULL",1,"order_item_id"));assertThatThrownBy(()->requireCompatible(keys)).isInstanceOf(IllegalStateException.class);}}
}
