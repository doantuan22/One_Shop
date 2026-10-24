package com.oneshop.dto.request;

import com.oneshop.entity.PromotionStatus;
import com.oneshop.entity.PromotionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The Administrator's form for a promotion code. Field rules that depend on each other (a percentage is 1 to 100, a
 * cap belongs to a percentage only, the end is after the start, the limit is not below the uses already spent) are
 * checked by the Service.
 */
public class PromotionRequest {

    @NotBlank(message = "Vui lòng nhập mã khuyến mãi")
    @Size(max = 50, message = "Mã tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Vui lòng nhập tên khuyến mãi")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    @NotNull(message = "Vui lòng chọn loại giảm giá")
    private PromotionType discountType = PromotionType.PERCENT;

    @NotNull(message = "Vui lòng nhập mức giảm")
    @DecimalMin(value = "0.01", message = "Mức giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.01", message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Vui lòng nhập tiền hàng tối thiểu (0 nếu không yêu cầu)")
    @DecimalMin(value = "0", message = "Tiền hàng tối thiểu không được âm")
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @NotNull(message = "Vui lòng nhập số lượt sử dụng")
    @Min(value = 1, message = "Số lượt sử dụng tối thiểu là 1")
    @Max(value = 10_000_000, message = "Số lượt sử dụng quá lớn")
    private Integer usageLimit;

    @NotNull(message = "Vui lòng nhập số lần mỗi khách được dùng")
    @Min(value = 1, message = "Mỗi khách được dùng tối thiểu 1 lần")
    @Max(value = 1000, message = "Số lần mỗi khách được dùng quá lớn")
    private Integer perUserLimit = 1;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private PromotionStatus status = PromotionStatus.ACTIVE;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PromotionType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(PromotionType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public void setStatus(PromotionStatus status) {
        this.status = status;
    }
}
