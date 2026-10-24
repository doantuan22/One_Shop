package com.oneshop.repository;

import com.oneshop.entity.OrderReturn;
import com.oneshop.entity.OrderReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Return requests. Every writer takes the Order write lock first ({@code OrderRepository.findByIdForUpdate}), so two
 * decisions or two handlings of one return serialize; the unique key on {@code order_id} stops a second request.
 */
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<OrderReturn> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<OrderReturn> findByOrderIdAndUserId(Long orderId, Long userId);

    @EntityGraph(attributePaths = {"user", "reviewedBy", "order"})
    @Query("select r from OrderReturn r where r.id = :id")
    Optional<OrderReturn> findDetailById(@Param("id") Long id);

    /** The Order a return belongs to, read before the Order lock is taken. */
    @Query("select r.order.id from OrderReturn r where r.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReturn r where r.id = :id")
    Optional<OrderReturn> findByIdForUpdate(@Param("id") Long id);

    /** Admin list: optional status, oldest request first for REQUESTED work, newest first otherwise. */
    @EntityGraph(attributePaths = {"user", "reviewedBy", "order"})
    @Query(value = "select r from OrderReturn r where (:status is null or r.status = :status)"
            + " order by r.requestedAt desc, r.id desc",
            countQuery = "select count(r) from OrderReturn r where (:status is null or r.status = :status)")
    Page<OrderReturn> search(@Param("status") OrderReturnStatus status, Pageable pageable);

    long countByStatus(OrderReturnStatus status);
}
