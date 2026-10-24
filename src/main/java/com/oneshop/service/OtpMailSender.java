package com.oneshop.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Sends the one-time code by SMTP. The server comes only from configuration (spring.mail.*); when it is missing or
 * refuses the message the caller gets {@link MailUnavailableException} and nothing is claimed as sent.
 */
@Component
public class OtpMailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;

    public OtpMailSender(ObjectProvider<JavaMailSender> mailSender,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${oneshop.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank() && mailSender.getIfAvailable() != null;
    }

    /** The code is only ever in the message body: it is never logged. */
    public void sendOtp(String to, String fullName, String otp, int validMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!isConfigured() || sender == null) {
            throw new MailUnavailableException("Hệ thống chưa cấu hình gửi email.", null);
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "OneShop");
            helper.setTo(to);
            helper.setSubject("Mã OTP đặt lại mật khẩu OneShop");
            String name = fullName == null || fullName.isBlank() ? "bạn" : fullName.trim();
            helper.setText("Xin chào " + name + ",\n\n"
                    + "Mã OTP đặt lại mật khẩu OneShop của bạn là:\n\n"
                    + "    " + otp + "\n\n"
                    + "Mã có hiệu lực trong " + validMinutes + " phút và chỉ dùng được một lần.\n"
                    + "Không chia sẻ mã này cho bất kỳ ai, kể cả nhân viên OneShop.\n\n"
                    + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này; mật khẩu của bạn không thay đổi.\n\n"
                    + "OneShop");
            sender.send(message);
        } catch (Exception ex) {
            // the cause is kept for the operator; the customer only learns that the mail could not be sent
            throw new MailUnavailableException("Không gửi được email lúc này. Vui lòng thử lại sau.", ex);
        }
    }

    public static class MailUnavailableException extends RuntimeException {
        public MailUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
