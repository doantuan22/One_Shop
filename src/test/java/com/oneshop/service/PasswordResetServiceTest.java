package com.oneshop.service;

import com.oneshop.entity.PasswordResetOtp;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.PasswordResetOtpRepository;
import com.oneshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The forgot-password rules, with the repositories and the mail server replaced by mocks. */
class PasswordResetServiceTest {

    private static final String EMAIL = "khach@example.com";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 0, 0);

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository users;
    private PasswordResetOtpRepository otps;
    private OtpMailSender mailer;
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        otps = mock(PasswordResetOtpRepository.class);
        mailer = mock(OtpMailSender.class);
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new PasswordResetService(users, otps, encoder, mailer, clock, "test-secret-test-secret-1234567890");

        user = new User();
        user.setEmail(EMAIL);
        user.setFullName("Khách Hàng");
        user.setRole(new Role(RoleName.CUSTOMER));
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(users.updatePasswordHash(anyString(), anyString(), any())).thenReturn(1);
        when(otps.consume(any(), any())).thenReturn(1);
    }

    private PasswordResetOtp otpRow(Long userId, String email, String code, LocalDateTime expires, int attempts) {
        PasswordResetOtp row = new PasswordResetOtp(userId, email, encoder.encode(code), expires, NOW.minusMinutes(1));
        ReflectionTestUtils.setField(row, "id", 99L);
        row.setAttempts(attempts);
        return row;
    }

    // ------------------------------------------------------------------ requesting a code

    @Test
    void anUnknownEmailIsRefusedAndNothingIsSent() {
        assertThatThrownBy(() -> service.requestOtp("nobody@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa được đăng ký")
                .extracting(e -> ((BadRequestException) e).getField()).isEqualTo("email");
        verify(mailer, never()).sendOtp(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(otps, never()).save(any());
    }

    @Test
    void aDisabledAccountGetsNoCode() {
        ReflectionTestUtils.setField(user, "status", com.oneshop.entity.ActiveStatus.INACTIVE);
        assertThatThrownBy(() -> service.requestOtp(EMAIL)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vô hiệu hóa");
        verify(mailer, never()).sendOtp(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void anExistingEmailGetsASixDigitCodeStoredOnlyAsAHashAndBoundToItsAccount() {
        when(otps.findFirstByEmailOrderByIdDesc(EMAIL)).thenReturn(Optional.empty());
        service.requestOtp("  Khach@Example.com ");

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendOtp(eq(EMAIL), eq("Khách Hàng"), sent.capture(), eq(10));
        assertThat(sent.getValue()).matches(Pattern.compile("\\d{6}"));

        ArgumentCaptor<PasswordResetOtp> saved = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(otps).save(saved.capture());
        PasswordResetOtp row = saved.getValue();
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getEmail()).isEqualTo(EMAIL);
        assertThat(row.getOtpHash()).isNotEqualTo(sent.getValue());
        assertThat(encoder.matches(sent.getValue(), row.getOtpHash())).isTrue();
        assertThat(row.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        verify(otps).consumeAllOpen(EMAIL, NOW);
    }

    @Test
    void aSecondRequestWithinAMinuteIsRefused() {
        PasswordResetOtp last = new PasswordResetOtp(7L, EMAIL, "h", NOW.plusMinutes(9), NOW.minusSeconds(20));
        when(otps.findFirstByEmailOrderByIdDesc(EMAIL)).thenReturn(Optional.of(last));
        assertThatThrownBy(() -> service.requestOtp(EMAIL)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đợi 40 giây");
        verify(mailer, never()).sendOtp(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void tooManyRequestsInAnHourAreRefused() {
        when(otps.findFirstByEmailOrderByIdDesc(EMAIL)).thenReturn(Optional.empty());
        when(otps.countByEmailAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.requestOtp(EMAIL)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("quá nhiều lần");
        verify(mailer, never()).sendOtp(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void whenTheMailCannotBeSentTheFailureIsReported() {
        when(otps.findFirstByEmailOrderByIdDesc(EMAIL)).thenReturn(Optional.empty());
        doThrow(new OtpMailSender.MailUnavailableException("Không gửi được email lúc này.", null))
                .when(mailer).sendOtp(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        assertThatThrownBy(() -> service.requestOtp(EMAIL)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không gửi được email");
    }

    // ------------------------------------------------------------------ step 2: proving the code

    private void openCode(PasswordResetOtp row) {
        when(otps.findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(EMAIL)).thenReturn(Optional.of(row));
        when(otps.findById(99L)).thenReturn(Optional.of(row));
    }

    @Test
    void theRightCodeGivesATicketButIsNotUsedUpAndChangesNoPassword() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));

        String ticket = service.verifyOtp(EMAIL, " 123456 ");

        assertThat(ticket).isNotBlank();
        verify(otps, never()).consume(any(), any());
        verify(users, never()).updatePasswordHash(anyString(), anyString(), any());
    }

    @Test
    void aWrongCodeGivesNoTicketAndCountsATry() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 1));

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("còn 3 lần");
        verify(otps).addAttempt(99L);
    }

    @Test
    void theFifthWrongCodeKillsTheCode() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 4));

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("quá nhiều lần");
        verify(otps).consume(eq(99L), any());
    }

    @Test
    void anExpiredCodeIsRefusedEvenIfItIsRight() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.minusSeconds(1), 0));

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("hết hạn");
    }

    @Test
    void aCodeOfAnotherAccountOrAnotherEmailNeverVerifies() {
        openCode(otpRow(8L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456")).isInstanceOf(BadRequestException.class);

        openCode(otpRow(7L, "other@example.com", "123456", NOW.plusMinutes(5), 0));
        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyingForAnUnknownEmailOrWithoutAnOpenCodeIsRefused() {
        assertThatThrownBy(() -> service.verifyOtp("nobody@example.com", "123456"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("chưa được đăng ký");
        when(otps.findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456")).isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------------ step 3: the new password

    @Test
    void withTheTicketThePasswordOfThatEmailsAccountIsChangedAndTheCodeUsedOnce() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        String ticket = service.verifyOtp(EMAIL, "123456");

        service.resetPassword(EMAIL, ticket, "NewPass#2026", "NewPass#2026");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).updatePasswordHash(eq(EMAIL), hash.capture(), any());
        assertThat(encoder.matches("NewPass#2026", hash.getValue())).isTrue();
        verify(otps).consume(eq(99L), any());
    }

    @Test
    void withoutATicketOrWithAForgedOneNothingChanges() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        String real = service.verifyOtp(EMAIL, "123456");

        for (String bad : new String[] {null, "", "x", "99.9999999999.AAAA", real + "x", real.substring(0, real.length() - 3)}) {
            assertThatThrownBy(() -> service.resetPassword(EMAIL, bad, "NewPass#2026", "NewPass#2026"))
                    .isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getField()).isEqualTo("ticket");
        }
        verify(users, never()).updatePasswordHash(anyString(), anyString(), any());
        verify(otps, never()).consume(any(), any());
    }

    @Test
    void aTicketOfOneEmailCannotResetAnotherAccount() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        String ticket = service.verifyOtp(EMAIL, "123456");

        User other = new User();
        other.setEmail("other@example.com");
        other.setRole(new Role(RoleName.CUSTOMER));
        ReflectionTestUtils.setField(other, "id", 8L);
        when(users.findByEmail("other@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.resetPassword("other@example.com", ticket, "NewPass#2026", "NewPass#2026"))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).updatePasswordHash(anyString(), anyString(), any());
    }

    @Test
    void aTicketDiesWithItsCode() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        String ticket = service.verifyOtp(EMAIL, "123456");

        // the code is replaced or used elsewhere: the stored row is no longer open
        PasswordResetOtp used = otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0);
        ReflectionTestUtils.setField(used, "consumedAt", NOW);
        when(otps.findById(99L)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.resetPassword(EMAIL, ticket, "NewPass#2026", "NewPass#2026"))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).updatePasswordHash(anyString(), anyString(), any());
    }

    @Test
    void aMismatchedConfirmationDoesNotBurnTheCode() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        String ticket = service.verifyOtp(EMAIL, "123456");

        assertThatThrownBy(() -> service.resetPassword(EMAIL, ticket, "NewPass#2026", "Other#2026"))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getField()).isEqualTo("confirmPassword");
        verify(otps, never()).consume(any(), any());
        verify(users, never()).updatePasswordHash(anyString(), anyString(), any());
    }

    @Test
    void aCodeThatSomeoneElseJustUsedCannotBeUsedTwice() {
        openCode(otpRow(7L, EMAIL, "123456", NOW.plusMinutes(5), 0));
        String ticket = service.verifyOtp(EMAIL, "123456");
        when(otps.consume(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword(EMAIL, ticket, "NewPass#2026", "NewPass#2026"))
                .isInstanceOf(BadRequestException.class);
        verify(users, times(0)).updatePasswordHash(anyString(), anyString(), any());
    }
}
