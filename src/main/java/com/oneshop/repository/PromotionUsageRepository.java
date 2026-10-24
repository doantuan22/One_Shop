package com.oneshop.repository;

import com.oneshop.entity.PromotionUsage;
import com.oneshop.entity.PromotionUsageStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    /** How many times this customer's checkouts hold the promotion (RELEASED uses no longer count). */
    long countByPromotionIdAndUserIdAndStatus(Long promotionId, Long userId, PromotionUsageStatus status);

    /** Per-promotion use counts of one customer, for the checkout page (one query for every code shown). */
    @Query("select u.promotion.id as promotionId, count(u) as total from PromotionUsage u where u.user.id = :userId"
            + " and u.status = com.oneshop.entity.PromotionUsageStatus.USED group by u.promotion.id")
    List<UsedCount> countUsedByUser(@Param("userId") Long userId);

    interface UsedCount {
        Long getPromotionId();

        long getTotal();
    }

    Optional<PromotionUsage> findByCheckoutSessionId(Long checkoutId);

    boolean existsByPromotionId(Long promotionId);

    /** Latest uses of one promotion for the Admin screen. */
    @EntityGraph(attributePaths = {"user", "checkoutSession"})
    List<PromotionUsage> findTop20ByPromotionIdOrderByUsedAtDescIdDesc(Long promotionId);
}
