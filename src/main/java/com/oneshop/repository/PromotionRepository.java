package com.oneshop.repository;

import com.oneshop.entity.Promotion;
import com.oneshop.entity.PromotionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Promotion codes. Spending or giving back a use always goes through the locked read ({@link #findByIdForUpdate}). */
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    /** The code is stored upper-case; the lookup is case-insensitive anyway (database collation). */
    Optional<Promotion> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Lock order: promotions are locked last (after Cart, StoreProducts, Order), one row at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Long id);

    /** The id of a code, read without a lock so that the locked read can follow in the agreed order. */
    @Query("select p.id from Promotion p where lower(p.code) = lower(:code)")
    Optional<Long> findIdByCode(@Param("code") String code);

    /** Codes a customer may see at checkout: ACTIVE, inside their dates and with uses left. Cheapest-to-use first. */
    @Query("select p from Promotion p where p.status = com.oneshop.entity.PromotionStatus.ACTIVE"
            + " and (p.startAt is null or p.startAt <= :now) and (p.endAt is null or p.endAt >= :now)"
            + " and p.usedCount < p.usageLimit order by p.minOrderAmount asc, p.id asc")
    List<Promotion> findOpen(@Param("now") LocalDateTime now);

    /** Admin list: optional status and a text that matches the code or the name. */
    @Query(value = "select p from Promotion p where (:status is null or p.status = :status)"
            + " and (:q = '' or lower(p.code) like :like escape '!' or lower(p.name) like :like escape '!')"
            + " order by p.createdAt desc, p.id desc",
            countQuery = "select count(p) from Promotion p where (:status is null or p.status = :status)"
                    + " and (:q = '' or lower(p.code) like :like escape '!' or lower(p.name) like :like escape '!')")
    Page<Promotion> search(@Param("status") PromotionStatus status, @Param("q") String q, @Param("like") String like,
                           Pageable pageable);
}
