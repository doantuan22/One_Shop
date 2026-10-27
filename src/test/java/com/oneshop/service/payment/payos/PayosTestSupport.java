package com.oneshop.service.payment.payos;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Builders for in-memory orders / payOS payments used by the payOS unit tests. */
final class PayosTestSupport {

    static final String EMAIL = "alice@test";

    private PayosTestSupport() {
    }

    static Order order(long id, LocalDateTime createdAt, String total) {
        User owner = new User();
        owner.setEmail(EMAIL);
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setUser(owner);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setTotalAmount(new BigDecimal(total));
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        return order;
    }

    static Payment payos(long id, Order order, long orderCode) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", id);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setProviderOrderCode(orderCode);
        payment.setTransactionCode("PAYOS-" + orderCode);
        return payment;
    }

    /** Evidence of a full payment as stored by {@link PayosEvidence}. */
    static Payment paid(Payment payment, LocalDateTime bankTime) {
        payment.setProviderStatus("PAID");
        payment.setProviderAmountPaid(payment.getAmount().longValueExact());
        payment.setProviderReference("TF" + payment.getProviderOrderCode());
        payment.setProviderPaidAt(bankTime);
        return payment;
    }
}
