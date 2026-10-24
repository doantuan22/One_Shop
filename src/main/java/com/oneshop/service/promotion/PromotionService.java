package com.oneshop.service.promotion;

import com.oneshop.entity.CheckoutSession;
import com.oneshop.entity.Promotion;
import com.oneshop.entity.PromotionStatus;
import com.oneshop.entity.PromotionType;
import com.oneshop.entity.PromotionUsage;
import com.oneshop.entity.PromotionUsageStatus;
import com.oneshop.entity.User;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.repository.PromotionRepository;
import com.oneshop.repository.PromotionUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The checkout side of promotion codes. One code per checkout, a discount on the GOODS only, never on the shipping fee.
 *
 * <p>Every condition is decided in {@link #problem}: ACTIVE, inside its dates, uses left ({@code usedCount <
 * usageLimit}), the customer's own uses below {@code perUserLimit}, and the goods reach {@code minOrderAmount}. The
 * checkout page only reads ({@link #options}, {@link #check}); placing the order calls {@link #lockAndEvaluate}, which
 * locks the promotion row (the last lock of the checkout) and decides again, then {@link #commitUse} spends the use
 * in the same transaction. Two customers racing for the last use serialize on that row, so the limit can never be
 * exceeded, and the database keeps {@code used_count <= usage_limit} as the last line of defence.
 */
@Service
public class PromotionService {

    /** A code the customer may pick, with whether it works for this cart right now and why not. */
    public record Option(String code, String name, String description, PromotionType type, BigDecimal value,
                         BigDecimal maxDiscount, BigDecimal minOrderAmount, int remaining, LocalDateTime endAt,
                         boolean eligible, String reason, BigDecimal discount) {
    }

    /** The code applied to a preview: what it is and how much it takes off. */
    public record Applied(String code, String name, BigDecimal discount) {
    }

    /** Result of reading a code for the page: either it applies, or {@code problem} says why not. */
    public record Check(Applied applied, String problem) {
    }

    /** A promotion locked for this transaction and the discount it gives the checkout (whole checkout, before splitting). */
    public record Evaluation(Promotion promotion, BigDecimal discount) {
    }

    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final Clock clock;

    public PromotionService(PromotionRepository promotions, PromotionUsageRepository usages, Clock clock) {
        this.promotions = promotions;
        this.usages = usages;
        this.clock = clock;
    }

    /** Upper-case, trimmed code; {@code null} when nothing was typed or picked. */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String clean = code.trim().toUpperCase(Locale.ROOT);
        return clean.isEmpty() ? null : clean;
    }

    // ------------------------------------------------------------------ the checkout page (read only)

    /** Every open code, each marked eligible or not for a cart whose goods are worth {@code goods}. */
    @Transactional(readOnly = true)
    public List<Option> options(Long userId, BigDecimal goods) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Long> used = new HashMap<>();
        usages.countUsedByUser(userId).forEach(u -> used.put(u.getPromotionId(), u.getTotal()));
        return promotions.findOpen(now).stream().map(p -> {
            String reason = problem(p, used.getOrDefault(p.getId(), 0L), goods, now);
            BigDecimal discount = reason == null ? PromotionCalculator.discountFor(p, goods) : null;
            return new Option(p.getCode(), p.getName(), p.getDescription(), p.getDiscountType(), p.getDiscountValue(),
                    p.getMaxDiscountAmount(), p.getMinOrderAmount(), p.getRemaining(), p.getEndAt(), reason == null,
                    reason, discount);
        }).toList();
    }

    /** Reads one code for the preview: no lock, no use spent; the same rules as placing the order. */
    @Transactional(readOnly = true)
    public Check check(Long userId, String code, BigDecimal goods) {
        String clean = normalize(code);
        if (clean == null) {
            return new Check(null, null);
        }
        Optional<Promotion> found = promotions.findByCodeIgnoreCase(clean);
        if (found.isEmpty()) {
            return new Check(null, "Mã khuyến mãi không tồn tại.");
        }
        Promotion p = found.get();
        String problem = problem(p, usedBy(p, userId), goods, LocalDateTime.now(clock));
        return problem != null ? new Check(null, problem)
                : new Check(new Applied(p.getCode(), p.getName(), PromotionCalculator.discountFor(p, goods)), null);
    }

    // ------------------------------------------------------------------ placing the order

    /**
     * Locks the code and decides again, with the goods of THIS checkout. Must be called after the Cart and the
     * StoreProducts were locked, so the promotion is the last lock.
     *
     * @return {@code null} when no code was given
     * @throws CheckoutConflictException {@code PROMOTION_UNAVAILABLE} when the code cannot be used any more
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Evaluation lockAndEvaluate(User user, String code, BigDecimal goods) {
        String clean = normalize(code);
        if (clean == null) {
            return null;
        }
        Long id = promotions.findIdByCode(clean).orElseThrow(() -> unavailable("Mã khuyến mãi không tồn tại."));
        Promotion p = promotions.findByIdForUpdate(id).orElseThrow(() -> unavailable("Mã khuyến mãi không tồn tại."));
        String problem = problem(p, usedBy(p, user.getId()), goods, LocalDateTime.now(clock));
        if (problem != null) {
            throw unavailable(problem);
        }
        return new Evaluation(p, PromotionCalculator.discountFor(p, goods));
    }

    /** Spends one use for this checkout (the promotion row is still locked by {@link #lockAndEvaluate}). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commitUse(Evaluation evaluation, User user, CheckoutSession session) {
        Promotion p = evaluation.promotion();
        p.setUsedCount(p.getUsedCount() + 1);
        promotions.saveAndFlush(p);
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotion(p);
        usage.setUser(user);
        usage.setCheckoutSession(session);
        usage.setDiscountAmount(evaluation.discount());
        usage.setStatus(PromotionUsageStatus.USED);
        usage.setUsedAt(LocalDateTime.now(clock).withNano(0));
        usages.save(usage);
    }

    // ------------------------------------------------------------------ rules

    /**
     * Why the code cannot be used for this customer and these goods, or {@code null} when it can. The promotion's
     * {@code usedCount} is read from the row, which is locked when this runs on the order path.
     */
    String problem(Promotion p, long usedByCustomer, BigDecimal goods, LocalDateTime now) {
        if (p.getStatus() != PromotionStatus.ACTIVE) {
            return "Mã khuyến mãi đã ngừng áp dụng.";
        }
        if (p.getStartAt() != null && now.isBefore(p.getStartAt())) {
            return "Mã khuyến mãi chưa đến thời gian áp dụng.";
        }
        if (p.getEndAt() != null && now.isAfter(p.getEndAt())) {
            return "Mã khuyến mãi đã hết hạn.";
        }
        if (p.getUsedCount() >= p.getUsageLimit()) {
            return "Mã khuyến mãi đã hết lượt sử dụng.";
        }
        if (usedByCustomer >= p.getPerUserLimit()) {
            return "Bạn đã dùng hết số lần cho phép của mã này.";
        }
        if (goods == null || goods.compareTo(p.getMinOrderAmount()) < 0) {
            return "Cần tiền hàng từ " + vnd(p.getMinOrderAmount()) + " để dùng mã này.";
        }
        return null;
    }

    private long usedBy(Promotion p, Long userId) {
        return usages.countByPromotionIdAndUserIdAndStatus(p.getId(), userId, PromotionUsageStatus.USED);
    }

    private static CheckoutConflictException unavailable(String message) {
        return new CheckoutConflictException(CheckoutConflictException.CODE_PROMOTION_UNAVAILABLE, message);
    }

    /** 200000 becomes "200.000 ₫". */
    static String vnd(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        return format.format(amount) + " ₫";
    }
}
