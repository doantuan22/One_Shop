package com.oneshop;

import com.oneshop.service.allocation.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Deterministic generated cases respect the real SQL unique(store,product) precondition. Not E2E. */
class QaAuditAllocationInvariantTest {
    @ParameterizedTest @ValueSource(longs={37,58,99})
    void generatedAllocationConservesStockAndDemand(long seed) {
        Random random=new Random(seed);
        for(int iteration=0;iteration<1000;iteration++) {
            List<AllocationDemand> demand=new ArrayList<>();List<StockOffer> offers=new ArrayList<>();
            int products=1+random.nextInt(5),stores=1+random.nextInt(5);
            for(long p=1;p<=products;p++)demand.add(new AllocationDemand(p,1+random.nextInt(15)));
            for(long s=1;s<=stores;s++)for(long p=1;p<=products;p++)
                offers.add(new StockOffer(s*100+p,s,p,random.nextInt(10)));
            AllocationResult result=AllocationEngine.allocate(demand,offers);
            boolean feasible=demand.stream().allMatch(d->offers.stream().filter(o->o.productId().equals(d.productId()))
                    .mapToLong(StockOffer::available).sum()>=d.quantity());
            assertThat(result.success()).as("seed %s case %s feasibility",seed,iteration).isEqualTo(feasible);
            if(result.success()) {
                Map<Long,Integer> allocated=new HashMap<>();Set<Long> seen=new HashSet<>();
                for(var store:result.stores())for(var line:store.lines()) {
                    StockOffer offer=offers.stream().filter(o->o.storeProductId().equals(line.storeProductId())).findFirst().orElseThrow();
                    assertThat(seen.add(line.storeProductId())).isTrue();
                    assertThat(store.storeId()).isEqualTo(offer.storeId());
                    assertThat(line.quantity()).isBetween(1,offer.available());
                    allocated.merge(line.productId(),line.quantity(),Integer::sum);
                }
                for(var d:demand)assertThat(allocated.get(d.productId())).isEqualTo(d.quantity());
                Collections.shuffle(demand,random);Collections.shuffle(offers,random);
                assertThat(AllocationEngine.allocate(demand,offers).signature()).as("permutation determinism")
                        .isEqualTo(result.signature());
            }
        }
    }
}
