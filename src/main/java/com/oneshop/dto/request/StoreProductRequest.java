package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Admin form of a StoreProduct: a SKU sold at one Store with its own price, quantity and status (BR-02).
 * {@code storeId} and {@code productId} are only used when creating; an existing StoreProduct never changes them.
 */
public class StoreProductRequest {

    @NotNull(message = "Vui lòng chọn chi nhánh")
    private Long storeId;

    @NotNull(message = "Vui lòng chọn sản phẩm")
    private Long productId;

    @NotNull(message = "Vui lòng nhập giá")
    @DecimalMin(value = "0", message = "Giá không được âm")
    @Digits(integer = 16, fraction = 2, message = "Giá không hợp lệ")
    private BigDecimal price;

    @NotNull(message = "Vui lòng nhập tồn kho")
    @Min(value = 0, message = "Tồn kho không được âm")
    private Integer quantity = 0;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private ActiveStatus status = ActiveStatus.ACTIVE;

    /** Reason of a quantity change; stored in the InventoryMovement that records it. */
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public ActiveStatus getStatus() {
        return status;
    }

    public void setStatus(ActiveStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
