package com.oneshop.seed;

import com.oneshop.seed.LargeSeedGenerator.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Generator of the large development seed: determinism, consistency rules 1-9 and proof that the checks catch faults. */
class LargeSeedGeneratorTest {

    // ---------------------------------------------------------------- (a) determinism

    @Test
    void sameInputGivesByteIdenticalOutput(@TempDir Path a, @TempDir Path b) throws IOException {
        LargeSeedGenerator.write(LargeSeedGenerator.generate(), a, Map.of());
        LargeSeedGenerator.write(LargeSeedGenerator.generate(), b, Map.of());
        for (String file : List.of("01_reference_data.sql", "02_catalog.sql", "03_transactions.sql", "04_reviews.sql", "images/image_plan.csv", "SEED_STATS.md")) {
            assertThat(Files.readString(a.resolve(file))).as(file).isEqualTo(Files.readString(b.resolve(file)));
        }
    }

    @Test
    void generatedSqlNeverDropsAndOnlyDeletesAboveId999(@TempDir Path dir) throws IOException {
        LargeSeedGenerator.write(LargeSeedGenerator.generate(), dir, Map.of());
        for (String file : List.of("01_reference_data.sql", "02_catalog.sql", "03_transactions.sql", "04_reviews.sql")) {
            String sql = Files.readString(dir.resolve(file));
            assertThat(sql).as(file).doesNotContain("DROP ").doesNotContain("TRUNCATE ");
            for (String line : sql.split("\n")) {
                if (line.startsWith("DELETE FROM")) assertThat(line).as(line).contains(">= 1000");
            }
        }
    }

    // ---------------------------------------------------------------- (b) rules 1-9 hold on the generated data

    @Test
    void generatedDataSatisfiesEveryRuleAndMinimumVolume() {
        assertThat(LargeSeedGenerator.validate(LargeSeedGenerator.generate())).isEmpty();
    }

    @Test
    void imagePlanIsUniqueAndFollowsTheSeedFolderConvention() {
        Model m = LargeSeedGenerator.generate();
        assertThat(m.imagePublicId.values()).doesNotHaveDuplicates();
        assertThat(m.imagePublicId.values()).allMatch(id -> id.startsWith("oneshop/seed/products/") || id.startsWith("oneshop/seed/brands/")
                || id.startsWith("oneshop/seed/categories/"));
        assertThat(m.imagePublicId.get("category:chong-nang")).isEqualTo("oneshop/seed/categories/chong-nang");
        assertThat(m.imagePublicId).containsKey("product:BBIA-CHEEK-06:1"); // the seven products of 04 get images too
        assertThat(m.imagePublicId).containsKey("brand:innisfree");
        // the three old static category images are re-uploaded; the files themselves stay in the repo
        for (String f : List.of("category-skincare.webp", "category-makeup.webp", "category-haircare.webp")) {
            assertThat(Path.of("src/main/resources/static/images/site/categories", f)).exists();
        }
    }

    // ---------------------------------------------------------------- the checks really catch faults

    private static void assertCaught(String rulePrefix, Consumer<Model> sabotage) {
        Model m = LargeSeedGenerator.generate();
        sabotage.accept(m);
        List<String> v = LargeSeedGenerator.validate(m);
        assertThat(v).as("violations after sabotage").isNotEmpty();
        assertThat(v.stream().anyMatch(x -> x.startsWith(rulePrefix)))
                .as(rulePrefix + " expected in " + v.stream().limit(5).collect(Collectors.toList())).isTrue();
    }

    @Test
    void negativeStockIsCaught() {
        assertCaught("R1", m -> m.storeProducts.get(0).quantity = -3);
        assertCaught("R1", m -> m.movements.get(0).after = -1);
    }

    @Test
    void stockThatDoesNotMatchTheMovementChainIsCaught() {
        assertCaught("R1", m -> {
            var moved = m.movements.stream().map(x -> x.storeProductId).collect(Collectors.toSet());
            m.storeProducts.stream().filter(s -> moved.contains(s.id)).findFirst().orElseThrow().quantity += 7;
        });
    }

    @Test
    void orderTotalThatDiffersFromItsItemsIsCaught() {
        assertCaught("R2", m -> m.checkouts.get(0).orders.get(0).total += 1000);
        assertCaught("R2", m -> m.checkouts.get(0).total += 1000);
    }

    @Test
    void itemFromAnotherStoreIsCaught() {
        assertCaught("R3", m -> {
            var o = m.checkouts.get(0).orders.get(0);
            var other = m.storeProducts.stream().filter(s -> s.storeId != o.storeId).findFirst().orElseThrow();
            o.items.get(0).storeProductId = other.id;
        });
    }

    @Test
    void brokenStatusHistoryIsCaught() {
        assertCaught("R4", m -> m.orders().stream().filter(o -> o.history.size() >= 3).findFirst().orElseThrow().history.get(1).oldStatus = "COMPLETED");
        assertCaught("R4", m -> { // a skipped state
            var o = m.orders().stream().filter(x -> x.fulfillment.equals("DELIVERY") && x.history.size() >= 4).findFirst().orElseThrow();
            o.history.remove(1);
            o.history.get(1).oldStatus = o.history.get(0).newStatus;
        });
        assertCaught("R4", m -> m.orders().stream().filter(o -> o.history.size() >= 2).findFirst().orElseThrow().history.get(1).by = 999_999L);
    }

    @Test
    void paymentThatContradictsTheOrderIsCaught() {
        assertCaught("R5", m -> m.orders().stream().filter(o -> o.status.equals("COMPLETED")).findFirst().orElseThrow().payment.status = "PENDING");
        assertCaught("R5", m -> m.orders().get(0).payment.amount += 1);
    }

    @Test
    void duplicatePickupCodeIsCaught() {
        assertCaught("R6", m -> {
            var withCode = m.orders().stream().filter(o -> o.pickupCode != null).collect(Collectors.toList());
            var first = withCode.get(0);
            var other = withCode.stream().filter(o -> o != first && o.storeId == first.storeId).findFirst().orElseThrow();
            other.pickupCode = first.pickupCode;
        });
    }

    @Test
    void cancelledOrderWithoutStockRestoreIsCaught() {
        assertCaught("R7", m -> {
            long cancelled = m.orders().stream().filter(o -> o.status.equals("CANCELLED")).findFirst().orElseThrow().id;
            m.movements.removeIf(x -> x.type.equals("CANCEL_ORDER") && x.orderId == cancelled);
        });
    }

    @Test
    void reviewWithoutCompletedOrderIsCaught() {
        assertCaught("R8", m -> m.reviews.get(0).orderId = m.orders().stream().filter(o -> !o.status.equals("COMPLETED")).findFirst().orElseThrow().id);
    }

    @Test
    void futureTimestampsAreCaught() {
        assertCaught("R9", m -> m.orders().get(0).createdAgo = -10);
        assertCaught("R9", m -> m.orders().get(0).updatedAgo = m.orders().get(0).createdAgo + 5);
    }

    @Test
    void minimumVolumesAreEnforced() {
        assertCaught("COUNT", m -> m.reviews.subList(10, m.reviews.size()).clear());
    }

    @Test
    void statsMentionEveryStatus() {
        String stats = LargeSeedGenerator.stats(LargeSeedGenerator.generate());
        Stream.of("COMPLETED", "CANCELLED", "PENDING_PAYMENT", "CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "READY_FOR_PICKUP")
                .forEach(s -> assertThat(stats).contains(s));
    }
}
