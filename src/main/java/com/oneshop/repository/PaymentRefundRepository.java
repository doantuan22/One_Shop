package com.oneshop.repository;

import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** The simulated refund ledger. Writers hold the Order write lock; the unique indexes are the last line of defence. */
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    /** Newest last: the history of one Order. */
    @EntityGraph(attributePaths = {"fulfillment", "requestedBy"})
    @Query("select r from PaymentRefund r where r.order.id = :orderId order by r.requestedAt, r.id")
    List<PaymentRefund> findByOrderIdOrdered(@Param("orderId") Long orderId);

    @Query("select r from PaymentRefund r where r.payment.id = :paymentId order by r.id")
    List<PaymentRefund> findByPaymentId(@Param("paymentId") Long paymentId);

    @Query("select r from PaymentRefund r where r.order.id = :orderId and r.status = :status order by r.id")
    List<PaymentRefund> findByOrderIdAndStatus(@Param("orderId") Long orderId,
                                               @Param("status") PaymentRefundStatus status);

    /** Money already given back, or being given back, for one Payment (failed simulations moved nothing). */
    @Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where r.payment.id = :paymentId and r.status <> "
            + "com.oneshop.entity.PaymentRefundStatus.SIMULATION_FAILED")
    BigDecimal sumCommitted(@Param("paymentId") Long paymentId);

    /** Money really paid out (REAL refunds confirmed with evidence) or simulated, for one Payment, by status. */
    @Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where r.payment.id = :paymentId and r.status = :status")
    BigDecimal sumByPaymentAndStatus(@Param("paymentId") Long paymentId, @Param("status") PaymentRefundStatus status);

    @Query("select r from PaymentRefund r where r.fulfillment.id = :fulfillmentId and r.component = "
            + "com.oneshop.entity.PaymentRefundComponent.GOODS")
    Optional<PaymentRefund> findGoodsRefund(@Param("fulfillmentId") Long fulfillmentId);
}
