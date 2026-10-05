package com.oneshop.dto.request;

import com.oneshop.entity.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin form of a Product. A Product is one sellable SKU shared by the whole chain (BR-01): it has no price and no
 * quantity, those belong to StoreProduct.
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

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }
}
