package com.oneshop.dto.request;

import com.oneshop.entity.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin form of a V3 Product: one SKU and one online price shared by the chain. Physical and reserved quantities
 * belong to StoreProduct; Staff inventory operations cannot change Product.price.
 */
public class ProductRequest {

    @NotNull(message = "Vui lòng chọn danh mục")
    private Long categoryId;

    @NotNull(message = "Vui lòng chọn thương hiệu")
    private Long brandId;

    @NotBlank(message = "Vui lòng nhập mã SKU")
    @Size(max = 50, message = "SKU tối đa 50 ký tự")
    private String sku;

    @NotBlank(message = "Vui lòng nhập tên sản phẩm")
    @Size(max = 255, message = "Tên tối đa 255 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Vui lòng nhập giá bán online")
    @jakarta.validation.constraints.DecimalMin(value = "0", message = "Giá không được âm")
    @jakarta.validation.constraints.Digits(integer = 16, fraction = 2, message = "Giá không hợp lệ")
    private java.math.BigDecimal price = java.math.BigDecimal.ZERO;

    @jakarta.validation.constraints.DecimalMin(value = "0", message = "Giá gốc không được âm")
    @jakarta.validation.constraints.Digits(integer = 16, fraction = 2, message = "Giá gốc không hợp lệ")
    private java.math.BigDecimal originalPrice;

    @jakarta.validation.constraints.AssertTrue(message = "Giá gốc phải lớn hơn hoặc bằng giá bán online")
    public boolean isOriginalPriceValid() {
        return originalPrice == null || price == null || originalPrice.compareTo(price) >= 0;
    }

    /** An ACTIVE product is sold, so it needs a real price; a "0 ₫" product would be listed but can never be bought. */
    @jakarta.validation.constraints.AssertTrue(message = "Giá bán online phải lớn hơn 0 khi sản phẩm đang bán")
    public boolean isActivePriceValid() {
        return status != ProductStatus.ACTIVE || price == null || price.signum() > 0;
    }

    @NotNull(message = "Vui lòng chọn trạng thái")
    private ProductStatus status = ProductStatus.ACTIVE;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
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

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public void setPrice(java.math.BigDecimal price) {
        this.price = price;
    }

    public java.math.BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(java.math.BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }
}
