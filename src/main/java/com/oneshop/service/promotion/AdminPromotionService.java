package com.oneshop.service.promotion;

import com.oneshop.dto.request.PromotionRequest;
import com.oneshop.dto.response.PromotionResponse;
import com.oneshop.entity.Promotion;
import com.oneshop.entity.PromotionStatus;
import com.oneshop.entity.PromotionType;
import com.oneshop.entity.PromotionUsage;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.PromotionRepository;
import com.oneshop.repository.PromotionUsageRepository;
import com.oneshop.service.query.Paging;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Administrator management of promotion codes: list, create, edit, delete.
 *
 * <ul>
 *   <li>The code is upper-case letters, digits, {@code _} and {@code -} (3 to 30 characters), unique, and cannot be
 *   changed after it was created (orders keep a copy of it).</li>
 *   <li>The number of uses cannot be set below the uses already spent.</li>
 *   <li>Editing the discount or the conditions only affects orders placed afterwards: an order keeps the amount it
 *   was given.</li>
 *   <li>A code that was never used can be deleted; a code that was used cannot (its history stays), but it can be
 *   switched to INACTIVE.</li>
 * </ul>
 * An Administrator editing a code takes its row lock, so a change never interleaves with a checkout spending a use.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminPromotionService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,30}$");

    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final Clock clock;

    public AdminPromotionService(PromotionRepository promotions, PromotionUsageRepository usages, Clock clock) {
        this.promotions = promotions;
        this.usages = usages;
        this.clock = clock;
    }

    public Page<PromotionResponse> list(PromotionStatus status, String q, int page) {
        String text = q == null ? "" : q.trim().toLowerCase();
        String like = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return promotions.search(status, text, like, Paging.page(page, 20)).map(this::response);
    }

    public PromotionResponse get(Long id) {
        return response(find(id));
    }

    /** The latest uses of a code, for the edit screen. */
    public List<UsageRow> recentUsages(Long id) {
        return usages.findTop20ByPromotionIdOrderByUsedAtDescIdDesc(id).stream().map(AdminPromotionService::row).toList();
    }

    /** @param customer name and email of the customer, for the Admin only */
    public record UsageRow(String customer, Long checkoutId, BigDecimal discount, String status, LocalDateTime usedAt) {
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        String code = PromotionService.normalize(request.getCode());
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BadRequestException("code", "Mã gồm 3 đến 30 ký tự: chữ không dấu, số, dấu _ và dấu -.");
        }
        if (promotions.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("code", "Mã khuyến mãi này đã tồn tại.");
        }
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        apply(promotion, request, 0);
        return response(promotions.saveAndFlush(promotion));
    }

    @Transactional
    public PromotionResponse update(Long id, PromotionRequest request) {
        Promotion promotion = promotions.findByIdForUpdate(id).orElseThrow(AdminPromotionService::notFound);
        apply(promotion, request, promotion.getUsedCount());
        return response(promotions.saveAndFlush(promotion));
    }

    @Transactional
    public void delete(Long id) {
        Promotion promotion = promotions.findByIdForUpdate(id).orElseThrow(AdminPromotionService::notFound);
        if (usages.existsByPromotionId(id) || promotion.getUsedCount() > 0) {
            throw new BadRequestException("Mã đã được khách hàng sử dụng nên không thể xóa (lịch sử đơn hàng được giữ lại). "
                    + "Hãy chuyển mã sang \"Ngừng áp dụng\".");
        }
        promotions.delete(promotion);
        promotions.flush();
    }

    // ------------------------------------------------------------------ rules

    private void apply(Promotion promotion, PromotionRequest request, int usedCount) {
        PromotionType type = request.getDiscountType();
        BigDecimal value = request.getDiscountValue();
        if (type == PromotionType.PERCENT) {
            if (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0
                    || value.stripTrailingZeros().scale() > 0) {
                throw new BadRequestException("discountValue", "Giảm theo phần trăm phải là số nguyên từ 1 đến 100.");
            }
        } else if (value.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("discountValue", "Số tiền giảm phải là số đồng nguyên (không có phần lẻ).");
        }
        BigDecimal cap = request.getMaxDiscountAmount();
        if (cap != null && type != PromotionType.PERCENT) {
            throw new BadRequestException("maxDiscountAmount", "Mức giảm tối đa chỉ dùng cho giảm theo phần trăm.");
        }
        if (cap != null && cap.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("maxDiscountAmount", "Mức giảm tối đa phải là số đồng nguyên.");
        }
        BigDecimal min = request.getMinOrderAmount();
        if (min.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("minOrderAmount", "Tiền hàng tối thiểu phải là số đồng nguyên.");
        }
        if (request.getStartAt() != null && request.getEndAt() != null
                && !request.getEndAt().isAfter(request.getStartAt())) {
            throw new BadRequestException("endAt", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (request.getUsageLimit() < usedCount) {
            throw new BadRequestException("usageLimit", "Số lượt sử dụng không được nhỏ hơn số lượt đã dùng (" + usedCount + ").");
        }
        promotion.setName(request.getName().trim());
        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        promotion.setDescription(description.isEmpty() ? null : description);
        promotion.setDiscountType(type);
        promotion.setDiscountValue(value.setScale(2, RoundingMode.UNNECESSARY));
        promotion.setMaxDiscountAmount(cap == null ? null : cap.setScale(2, RoundingMode.UNNECESSARY));
        promotion.setMinOrderAmount(min.setScale(2, RoundingMode.UNNECESSARY));
        promotion.setUsageLimit(request.getUsageLimit());
        promotion.setPerUserLimit(request.getPerUserLimit());
        promotion.setStartAt(request.getStartAt() == null ? null : request.getStartAt().withNano(0));
        promotion.setEndAt(request.getEndAt() == null ? null : request.getEndAt().withNano(0));
        promotion.setStatus(request.getStatus());
    }

    private Promotion find(Long id) {
        return promotions.findById(id).orElseThrow(AdminPromotionService::notFound);
    }

    private PromotionResponse response(Promotion p) {
        LocalDateTime now = LocalDateTime.now(clock);
        String state = p.getStatus() != PromotionStatus.ACTIVE ? "INACTIVE"
                : p.getEndAt() != null && now.isAfter(p.getEndAt()) ? "EXPIRED"
                : p.getUsedCount() >= p.getUsageLimit() ? "EXHAUSTED"
                : p.getStartAt() != null && now.isBefore(p.getStartAt()) ? "SCHEDULED" : "ACTIVE";
        return new PromotionResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getDiscountType(),
                p.getDiscountValue(), p.getMaxDiscountAmount(), p.getMinOrderAmount(), p.getUsageLimit(),
                p.getUsedCount(), p.getPerUserLimit(), p.getStartAt(), p.getEndAt(), p.getStatus(), state);
    }

    private static UsageRow row(PromotionUsage u) {
        return new UsageRow(u.getUser().getFullName() + " (" + u.getUser().getEmail() + ")",
                u.getCheckoutSession().getId(), u.getDiscountAmount(), u.getStatus().name(), u.getUsedAt());
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy mã khuyến mãi.");
    }
}
