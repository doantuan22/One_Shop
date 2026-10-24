package com.oneshop.repository;

import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    // Explicit FK predicate avoids the derived query's unnecessary orders join during locked reconciliation.
    @Query("select p from Payment p where p.order.id = :orderId order by p.id")
    List<Payment> findByOrderIdOrderByIdAsc(@Param("orderId") Long orderId);

    /**
     * Caller holds this Order's write lock. No legitimate concurrent writer can own one of its Payment rows.
     * READPAST therefore skips only unrelated Orders' locked rows when SQL Server scans the unindexed FK;
     * it avoids a cycle between two already flushed attempts without dirty reads or a schema change.
     * Ordinary views must use the regular committed query above.
     */
    @Query(value = "select p.* from dbo.payments p with (readpast, readcommittedlock, rowlock) "
            + "where p.order_id = :orderId order by p.payment_id", nativeQuery = true)
    List<Payment> findForLockedOrder(@Param("orderId") Long orderId);

    Optional<Payment> findByIdAndOrderId(Long paymentId, Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByIdAsc(Long orderId, PaymentStatus status);
}
