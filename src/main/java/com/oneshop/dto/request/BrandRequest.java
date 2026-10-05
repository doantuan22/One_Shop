package com.oneshop.dto.request;

import com.oneshop.entity.VisibilityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The logo is not part of this form: it is uploaded to Cloudinary through its own action. */
public class BrandRequest {

    @NotBlank(message = "Vui lòng nhập tên thương hiệu")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private VisibilityStatus status = VisibilityStatus.ACTIVE;

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

    public VisibilityStatus getStatus() {
        return status;
    }

    public void setStatus(VisibilityStatus status) {
        this.status = status;
    }
}
