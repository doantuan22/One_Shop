package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Admin form of a StoreProduct: a SKU held at one Store with its own quantity and status. The online price is one
 * value per SKU, owned by {@code Product}. The legacy {@code price} property is retained only for older internal
 * callers/forms; services deliberately never read or persist it (V3).
 * {@code storeId} and {@code productId} are only used when creating; an existing StoreProduct never changes them.
 */
public class StoreProductRequest {

    @NotNull(message = "Vui lòng chọn chi nhánh")
    private Long storeId;

    @NotNull(message = "Vui lòng chọn sản phẩm")
    private Long productId;

    /**
     * Legacy compatibility for internal callers only. MVC forms must not submit a Store-specific price: the only
     * online price is Product.price.
     */
    @Null(message = "Giá online là giá chung của sản phẩm; không thể sửa giá riêng theo chi nhánh.")
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
