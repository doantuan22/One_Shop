package com.oneshop.service.promotion;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Promotion;
import com.oneshop.entity.PromotionUsage;
import com.oneshop.entity.PromotionUsageStatus;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PromotionRepository;
import com.oneshop.repository.PromotionUsageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gives a promotion use back when the order it was spent on never happens. Called by every writer that moves an Order
 * to CANCELLED, inside the caller's transaction and after the Order status was written. One use belongs to a whole
 * checkout (a COD order split over several Stores spends ONE use), so it is released only when EVERY Order of that
 * checkout is CANCELLED. A returned Order (already delivered) never gets here: the use stays spent.
 *
 * <p>Lock order: the promotion is locked last, like at checkout. Under that lock the usage is read again, so two
 * Orders of one checkout cancelled at the same time release the use exactly once.
 */
@Component
public class PromotionReleaseService {

    private final PromotionUsageRepository usages;
    private final PromotionRepository promotions;
    private final OrderRepository orders;
    private final Clock clock;

    public PromotionReleaseService(PromotionUsageRepository usages, PromotionRepository promotions,
                                   OrderRepository orders, Clock clock) {
        this.usages = usages;
        this.promotions = promotions;
        this.orders = orders;
        this.clock = clock;
    }

    /** @return true when a use was given back now */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean releaseIfCancelled(Order cancelledOrder) {
        if (cancelledOrder == null || cancelledOrder.getPromotionCode() == null
                || cancelledOrder.getCheckoutSession() == null) {
            return false;
        }
        Long checkoutId = cancelledOrder.getCheckoutSession().getId();
        Optional<PromotionUsage> peek = usages.findByCheckoutSessionId(checkoutId);
        if (peek.isEmpty() || peek.get().getStatus() != PromotionUsageStatus.USED) {
            return false;
        }
        List<Order> siblings = orders.findByCheckoutSessionIdOrderByIdAsc(checkoutId);
        if (siblings.stream().anyMatch(o -> o.getOrderStatus() != OrderStatus.CANCELLED
                && !o.getId().equals(cancelledOrder.getId()))) {
            return false;
        }
        Promotion promotion = promotions.findByIdForUpdate(peek.get().getPromotion().getId()).orElse(null);
        PromotionUsage usage = usages.findByCheckoutSessionId(checkoutId).orElse(null);
        if (promotion == null || usage == null || usage.getStatus() != PromotionUsageStatus.USED) {
            return false;
        }
        usage.setStatus(PromotionUsageStatus.RELEASED);
        usage.setReleasedAt(LocalDateTime.now(clock).withNano(0));
        usages.save(usage);
        promotion.setUsedCount(Math.max(0, promotion.getUsedCount() - 1));
        promotions.saveAndFlush(promotion);
        return true;
    }
}
