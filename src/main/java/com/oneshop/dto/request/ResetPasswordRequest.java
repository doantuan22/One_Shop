package com.oneshop.dto.request;

import com.oneshop.dto.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Step 3 of "forgot password": the e-mail, the ticket given when its code was proved, and the new password typed
 * twice. The ticket is the only proof of the code: no code is ever sent here.
 */
public class ResetPasswordRequest {

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Phiên đặt lại mật khẩu không hợp lệ")
    @Size(max = 200, message = "Phiên đặt lại mật khẩu không hợp lệ")
    private String ticket;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
    @MaxUtf8Bytes(value = 72, message = "Mật khẩu tối đa 72 byte (ký tự có dấu chiếm 2-3 byte), hãy dùng mật khẩu ngắn hơn")
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    private String confirmPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
