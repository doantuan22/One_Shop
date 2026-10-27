package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundComponent;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A real refund becomes REFUNDED only with the bank transaction code of the money that went back. */
class ManualRefundServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentRefundRepository ledger = mock(PaymentRefundRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderStatusWriter writer = mock(OrderStatusWriter.class);
    private final ManualRefundService service = new ManualRefundService(orders, payments, ledger, users, writer);
    private Order order;
    private Payment payment;
    private PaymentRefund refund;

    @BeforeEach
    void setUp() {
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 7L);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setProviderOrderCode(555L);
        payment.setAmount(new BigDecimal("200000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRefundStatus(PaymentRefundStatus.MANUAL_REQUESTED);
        refund = new PaymentRefund();
        ReflectionTestUtils.setField(refund, "id", 9L);
        refund.setOrder(order);
        refund.setPayment(payment);
        refund.setComponent(PaymentRefundComponent.WHOLE_ORDER);
        refund.setAmount(payment.getAmount());
        refund.setStatus(PaymentRefundStatus.MANUAL_REQUESTED);
        refund.setReason("late");
        refund.setRequestedAt(LocalDateTime.now());

        User admin = new User();
        admin.setEmail("admin@test");
        admin.setStatus(com.oneshop.entity.ActiveStatus.ACTIVE);
        admin.setRole(new Role(RoleName.ADMIN));
        when(users.findByEmail("admin@test")).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@test", "x", List.of()));
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(ledger.findById(9L)).thenReturn(Optional.of(refund));
        when(ledger.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(payments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(ledger.sumByPaymentAndStatus(anyLong(), any())).thenReturn(payment.getAmount());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void confirmationWithEvidenceMakesTheRefundRealAndFinishesThePaymentAndOrder() {
        assertThat(service.confirm(7L, 9L, " FT26300077 ", "chuyển khoản")).isEqualTo(ManualRefundService.Result.CONFIRMED);

        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.REFUNDED);
        assertThat(refund.getEvidenceReference()).isEqualTo("FT26300077");
        assertThat(refund.getProcessedBy().getEmail()).isEqualTo("admin@test");
        assertThat(refund.getRefundedAt()).isNotNull();
        assertThat(refund.getProcessedAt()).isNotNull();
        assertThat(payment.getRefundStatus()).isEqualTo(PaymentRefundStatus.REFUNDED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
    }

    @Test
    void withoutUsableEvidenceNothingBecomesRefunded() {
        for (String bad : new String[]{null, "", "   ", "abc"}) {
            assertThatThrownBy(() -> service.confirm(7L, 9L, bad, null)).isInstanceOf(BadRequestException.class);
        }
        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
    }

    @Test
    void aRepeatedSubmitIsIdempotentButADifferentCodeIsRefused() {
        service.confirm(7L, 9L, "FT26300077", null);

        assertThat(service.confirm(7L, 9L, "FT26300077", null)).isEqualTo(ManualRefundService.Result.ALREADY_CONFIRMED);
        assertThatThrownBy(() -> service.confirm(7L, 9L, "FT99999999", null)).isInstanceOf(PaymentConflictException.class);
        assertThat(refund.getEvidenceReference()).isEqualTo("FT26300077");
    }

    @Test
    void theSameBankCodeCannotJustifyTwoRefunds() {
        when(ledger.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UX_payment_refunds_evidence"));
        assertThatThrownBy(() -> service.confirm(7L, 9L, "FT26300077", null)).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void aSimulatedRefundCannotBeConfirmedAsReal() {
        payment.setProvider(PaymentProvider.MOCK);
        refund.setStatus(PaymentRefundStatus.PENDING_SIMULATION);
        assertThatThrownBy(() -> service.confirm(7L, 9L, "FT26300077", null)).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void aPartialRefundFinishesThePaymentOnlyWhenAllTheMoneyIsOut() {
        refund.setComponent(PaymentRefundComponent.GOODS);
        refund.setAmount(new BigDecimal("150000.00"));
        when(ledger.sumByPaymentAndStatus(anyLong(), any())).thenReturn(new BigDecimal("150000.00"));

        service.confirm(7L, 9L, "FT26300077", null);

        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.REFUNDED);
        assertThat(payment.getRefundStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED); // 50 000 still owed
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
    }

    @Test
    void aRefundOfAnotherOrderIsNotFound() {
        Order other = new Order();
        ReflectionTestUtils.setField(other, "id", 99L);
        refund.setOrder(other);
        assertThatThrownBy(() -> service.confirm(7L, 9L, "FT26300077", null))
                .isInstanceOf(com.oneshop.exception.ResourceNotFoundException.class);
    }
}
