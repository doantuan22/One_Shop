package com.oneshop;

import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ReviewV3ConcurrencyDatabaseIntegrationTest extends ReviewV3TestSupport {
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) futures.add(pool.submit(() -> {
                start.await();
                try { task.call(); return "ok"; } catch (Exception ex) { return ex; }
            }));
            start.countDown();
            List<Object> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(45, TimeUnit.SECONDS));
            return result;
        } finally { pool.shutdownNow(); }
    }

    @Test void twoConcurrentReviewsOfOneItemCommitExactlyOneReview() throws Exception {
        for (int round = 0; round < 3; round++) {
            long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
            var outcomes = race(() -> submit(line), () -> submit(line));
            assertThat(outcomes).containsExactlyInAnyOrder("ok", outcomes.stream().filter(x -> !x.equals("ok")).findFirst().orElseThrow());
            assertThat(outcomes.stream().filter(x -> !x.equals("ok"))).singleElement().isInstanceOf(BadRequestException.class);
            assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isEqualTo(1);
        }
    }

    @Test void sameSkuDifferentReceivedItemsBothCommitReviews() throws Exception {
        for (int round = 0; round < 3; round++) {
            long order = partiallyReceived(true);
            receive(order, "OS-GOVAP");
            var outcomes = race(() -> submit(item(order, "OS-THUDUC")), () -> submit(item(order, "OS-GOVAP")));
            assertThat(outcomes).containsExactly("ok", "ok");
            assertThat(count("select count(*) from dbo.reviews where order_id=?", order)).isEqualTo(2);
        }
    }

    @Test void twoStaffRepliesSerializeIntoOneOfficialRow() throws Exception {
        for (int round = 0; round < 3; round++) {
            // the Gò Vấp Staff completes the Gò Vấp part at its own Store ...
            transfer(STAFF_GOVAP, "OS-GOVAP");
            long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
            // ... and is then moved to Thủ Đức, so two Staff of the same Store reply at once (each Staff works at one Store)
            transfer(STAFF_GOVAP, "OS-THUDUC");
            var outcomes = race(() -> { reply(STAFF_THUDUC, id, "Nhân viên A"); return null; },
                    () -> { reply(STAFF_GOVAP, id, "Nhân viên B"); return null; });
            assertThat(outcomes).containsExactly("ok", "ok");
            assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isEqualTo(1);
            var row = jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id);
            assertThat(row.get("content")).isIn("Nhân viên A", "Nhân viên B");
            assertThat(row.get("staff_user_id")).isIn(userId(STAFF_THUDUC), userId(STAFF_GOVAP));
        }
    }

    @Test void moderationAndReplyShareTheReviewLock() throws Exception {
        for (int round = 0; round < 3; round++) {
            long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
            var outcomes = race(() -> { reply(STAFF_THUDUC, id, "Đang xử lý"); return null; },
                    () -> admin(() -> { adminReviews.setStatus(id, VisibilityStatus.HIDDEN); return null; }));
            assertThat(outcomes.get(1)).isEqualTo("ok");
            assertThat(outcomes.get(0).equals("ok") || outcomes.get(0) instanceof AccessDeniedException).isTrue();
            assertThat(jdbc.queryForObject("select status from dbo.reviews where review_id=?", String.class, id)).isEqualTo("HIDDEN");
            assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isBetween(0, 1);
        }
    }
}
