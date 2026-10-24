package com.oneshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu chỉnh sửa thông tin cá nhân.
 * Tên đăng nhập (username) và email là thông tin định danh bất biến, không được phép chỉnh sửa.
 */
public class UpdateAccountRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ (8-20 ký tự số)")
    private String phone;

    public UpdateAccountRequest() {}

    public UpdateAccountRequest(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
