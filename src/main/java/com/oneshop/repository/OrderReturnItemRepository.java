package com.oneshop.repository;

import com.oneshop.entity.OrderReturnItem;
import com.oneshop.entity.ReturnDisposition;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, Long> {

    @EntityGraph(attributePaths = {"store", "handledBy"})
    List<OrderReturnItem> findByOrderReturnIdOrderByIdAsc(Long returnId);

    long countByOrderReturnIdAndDisposition(Long returnId, ReturnDisposition disposition);

    /** The Order of a returned line, read before the Order lock is taken. */
    @Query("select i.orderReturn.order.id from OrderReturnItem i where i.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderReturnItem i where i.id = :id")
    Optional<OrderReturnItem> findByIdForUpdate(@Param("id") Long id);

    /**
     * Staff work list: lines of APPROVED / COMPLETED returns that the given Stores delivered. {@code disposition} narrows
     * to waiting (PENDING) or done lines; null shows all. Waiting lines first, then oldest approval first.
     */
    @EntityGraph(attributePaths = {"store", "storeProduct.product", "orderReturn.order", "handledBy"})
    @Query(value = "select i from OrderReturnItem i where i.store.id in :storeIds"
            + " and i.orderReturn.status in (com.oneshop.entity.OrderReturnStatus.APPROVED,"
            + " com.oneshop.entity.OrderReturnStatus.COMPLETED)"
            + " and (:disposition is null or i.disposition = :disposition)"
            + " order by case when i.disposition = com.oneshop.entity.ReturnDisposition.PENDING then 0 else 1 end,"
            + " i.orderReturn.reviewedAt, i.id",
            countQuery = "select count(i) from OrderReturnItem i where i.store.id in :storeIds"
                    + " and i.orderReturn.status in (com.oneshop.entity.OrderReturnStatus.APPROVED,"
                    + " com.oneshop.entity.OrderReturnStatus.COMPLETED)"
                    + " and (:disposition is null or i.disposition = :disposition)")
    Page<OrderReturnItem> findForStores(@Param("storeIds") Collection<Long> storeIds,
                                        @Param("disposition") ReturnDisposition disposition, Pageable pageable);

    @Query("select count(i) from OrderReturnItem i where i.store.id in :storeIds"
            + " and i.orderReturn.status = com.oneshop.entity.OrderReturnStatus.APPROVED"
            + " and i.disposition = com.oneshop.entity.ReturnDisposition.PENDING")
    long countWaitingForStores(@Param("storeIds") Collection<Long> storeIds);
}
