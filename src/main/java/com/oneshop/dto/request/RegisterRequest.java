package com.oneshop.dto.request;

import com.oneshop.dto.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    private String fullName;

    /** 4-30 chars of letters, digits, '_' and '.'; never contains '@'. Trimmed by the setter. */
    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Size(min = 4, max = 30, message = "Tên đăng nhập từ 4 đến 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_.]*$",
            message = "Tên đăng nhập chỉ gồm chữ, số, dấu _ và dấu chấm, không chứa @ hay khoảng trắng")
    private String userName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @Pattern(regexp = "^$|^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
    @MaxUtf8Bytes(value = 72, message = "Mật khẩu tối đa 72 byte (ký tự có dấu chiếm 2-3 byte), hãy dùng mật khẩu ngắn hơn")
    private String password;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName == null ? null : userName.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
