package com.oneshop.service;

import com.oneshop.entity.PasswordResetOtp;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.PasswordResetOtpRepository;
import com.oneshop.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * "Forgot password" with a one-time code sent by e-mail, in three steps: (1) send the code, (2) prove it by typing it,
 * (3) only then choose the new password.
 *
 * <p>The account is decided by the e-mail alone and the code is bound to it: a code is created only for an e-mail that
 * belongs to an active account, it is stored against that account's id and that e-mail, and it can reset only that
 * account's password. A wrong code never touches anything but the attempt counter.
 */
@Service
public class PasswordResetService {

    static final int OTP_VALID_MINUTES = 10;
    static final int RESEND_COOLDOWN_SECONDS = 60;
    static final int MAX_SENDS_PER_HOUR = 5;
    static final int MAX_WRONG_TRIES = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetOtpRepository otps;
    private final PasswordEncoder encoder;
    private final OtpMailSender mailer;
    private final Clock clock;
    private final byte[] ticketKey;

    @Autowired
    public PasswordResetService(UserRepository users, PasswordResetOtpRepository otps, PasswordEncoder encoder,
                                OtpMailSender mailer, @Value("${app.jwt.secret:}") String secret) {
        this(users, otps, encoder, mailer, Clock.systemDefaultZone(), secret);
    }

    PasswordResetService(UserRepository users, PasswordResetOtpRepository otps, PasswordEncoder encoder,
                         OtpMailSender mailer, Clock clock, String secret) {
        this.users = users;
        this.otps = otps;
        this.encoder = encoder;
        this.mailer = mailer;
        this.clock = clock;
        this.ticketKey = ("password-reset-ticket:" + (secret == null ? "" : secret)).getBytes(StandardCharsets.UTF_8);
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Sends a new code to the e-mail of an existing, active account. Any mistake is a {@link BadRequestException}
     * against the {@code email} field; if the message cannot be sent, nothing is kept (the transaction rolls back).
     */
    @Transactional
    public void requestOtp(String rawEmail) {
        String email = normalize(rawEmail);
        User user = requireActiveAccount(email);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        otps.findFirstByEmailOrderByIdDesc(email).ifPresent(last -> {
            long wait = RESEND_COOLDOWN_SECONDS - Duration.between(last.getCreatedAt(), now).getSeconds();
            if (wait > 0) {
                throw new BadRequestException("email", "Vui lòng đợi " + wait + " giây trước khi gửi lại mã.");
            }
        });
        if (otps.countByEmailAndCreatedAtAfter(email, now.minusHours(1)) >= MAX_SENDS_PER_HOUR) {
            throw new BadRequestException("email", "Bạn đã yêu cầu mã quá nhiều lần. Vui lòng thử lại sau 1 giờ.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otps.consumeAllOpen(email, now);
        otps.save(new PasswordResetOtp(user.getId(), email, encoder.encode(code), now.plusMinutes(OTP_VALID_MINUTES), now));
        try {
            mailer.sendOtp(email, user.getFullName(), code, OTP_VALID_MINUTES);
        } catch (OtpMailSender.MailUnavailableException ex) {
            throw new BadRequestException("email", ex.getMessage());
        }
    }

    /**
     * Step 2: checks the code the customer typed. A right code is NOT used up here: it earns a short-lived signed
     * ticket that step 3 must present. A wrong code only counts a try (hence {@code noRollbackFor}); the code dies
     * after {@value #MAX_WRONG_TRIES} wrong tries or when it expires.
     *
     * @return the ticket for {@link #resetPassword}; it is tied to this code, this account and this e-mail
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public String verifyOtp(String rawEmail, String rawOtp) {
        String email = normalize(rawEmail);
        User user = requireActiveAccount(email);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        PasswordResetOtp row = otps.findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow(() -> new BadRequestException("otp",
                        "Chưa có mã OTP hợp lệ cho email này. Vui lòng gửi mã mới."));
        // the code must belong to exactly this account and this e-mail
        if (!user.getId().equals(row.getUserId()) || !email.equals(row.getEmail())) {
            otps.consumeAllOpen(email, now);
            throw new BadRequestException("otp", "Mã OTP không hợp lệ. Vui lòng gửi mã mới.");
        }
        if (!row.getExpiresAt().isAfter(now)) {
            otps.consume(row.getId(), now);
            throw new BadRequestException("otp", "Mã OTP đã hết hạn. Vui lòng gửi mã mới.");
        }
        if (row.getAttempts() >= MAX_WRONG_TRIES) {
            otps.consume(row.getId(), now);
            throw new BadRequestException("otp", "Bạn đã nhập sai quá nhiều lần. Vui lòng gửi mã mới.");
        }
        String otp = rawOtp == null ? "" : rawOtp.trim();
        if (!encoder.matches(otp, row.getOtpHash())) {
            otps.addAttempt(row.getId());
            int left = MAX_WRONG_TRIES - (row.getAttempts() + 1);
            if (left <= 0) {
                otps.consume(row.getId(), now);
                throw new BadRequestException("otp", "Bạn đã nhập sai quá nhiều lần. Vui lòng gửi mã mới.");
            }
            throw new BadRequestException("otp", "Mã OTP không đúng. Bạn còn " + left + " lần thử.");
        }
        return issueTicket(row);
    }

    /**
     * Step 3: sets the new password of the account that owns {@code rawEmail}, but only with the ticket step 2 gave
     * for that e-mail while its code is still open. The code is used up here, exactly once. The ticket is checked
     * first, so a wrong confirmation or a weak password never burns it.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void resetPassword(String rawEmail, String ticket, String newPassword, String confirmPassword) {
        String email = normalize(rawEmail);
        User user = requireActiveAccount(email);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        Long otpId = readTicket(ticket, email, user.getId(), now);
        PasswordResetOtp row = otpId == null ? null : otps.findById(otpId).orElse(null);
        if (row == null || row.getConsumedAt() != null || !row.getExpiresAt().isAfter(now)
                || !user.getId().equals(row.getUserId()) || !email.equals(row.getEmail())) {
            throw new BadRequestException("ticket", "Phiên đặt lại mật khẩu đã hết hạn. Vui lòng nhập lại mã OTP.");
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BadRequestException("confirmPassword", "Mật khẩu nhập lại không khớp");
        }
        if (otps.consume(row.getId(), now) != 1) {
            throw new BadRequestException("ticket", "Phiên đặt lại mật khẩu đã hết hạn. Vui lòng nhập lại mã OTP.");
        }
        if (users.updatePasswordHash(email, encoder.encode(newPassword), now) != 1) {
            throw new BadRequestException("email", "Tài khoản không còn hoạt động.");
        }
        otps.consumeAllOpen(email, now);
    }

    // ------------------------------------------------------------------ the ticket between step 2 and step 3

    /** {@code otpId.expiryEpochSecond.signature}; the signature covers the id, the expiry, the account and the e-mail. */
    private String issueTicket(PasswordResetOtp row) {
        long expires = row.getExpiresAt().atZone(clock.getZone()).toEpochSecond();
        String body = row.getId() + "." + expires;
        return body + "." + sign(body, row.getUserId(), row.getEmail());
    }

    /** @return the code id the ticket was issued for, or {@code null} when it is forged, for someone else, or expired */
    private Long readTicket(String ticket, String email, Long userId, LocalDateTime now) {
        if (ticket == null) {
            return null;
        }
        String[] parts = ticket.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String body = parts[0] + "." + parts[1];
            byte[] expected = sign(body, userId, email).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            long expires = Long.parseLong(parts[1]);
            if (expires <= now.atZone(clock.getZone()).toEpochSecond()) {
                return null;
            }
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sign(String body, Long userId, String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(ticketKey, "HmacSHA256"));
            byte[] signature = mac.doFinal((body + "|" + userId + "|" + email).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private User requireActiveAccount(String email) {
        if (email.isEmpty()) {
            throw new BadRequestException("email", "Vui lòng nhập email");
        }
        User user = users.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("email", "Email này chưa được đăng ký trong hệ thống."));
        if (!user.isActive()) {
            throw new BadRequestException("email", "Tài khoản này đã bị vô hiệu hóa. Vui lòng liên hệ OneShop.");
        }
        return user;
    }
}
