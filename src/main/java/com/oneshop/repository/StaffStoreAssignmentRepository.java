package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.StaffStoreAssignment;
import com.oneshop.entity.Store;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StaffStoreAssignmentRepository extends JpaRepository<StaffStoreAssignment, Long> {

    /** Store scope of a Staff: the Store comes from here, never from a request parameter (BR-14). */
    @EntityGraph(attributePaths = "store")
    List<StaffStoreAssignment> findByUserIdAndStatus(Long userId, ActiveStatus status);

    /** Staff accounts that currently work at a Store (ACTIVE assignment). */
    @Query("select a.user.id from StaffStoreAssignment a where a.status = com.oneshop.entity.ActiveStatus.ACTIVE")
    List<Long> findWorkingStaffIds();

    boolean existsByUserIdAndStoreIdAndStatus(Long userId, Long storeId, ActiveStatus status);

    Optional<StaffStoreAssignment> findByUserIdAndStoreId(Long userId, Long storeId);

    List<StaffStoreAssignment> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "user.role", "store"})
    @Query("select a from StaffStoreAssignment a where (:storeId is null or a.store.id = :storeId) order by a.id")
    Page<StaffStoreAssignment> searchAdmin(@Param("storeId") Long storeId, Pageable pageable);

    /**
     * Admin assignment console search: optional Store plus contains-match over the Staff's name / e-mail and an
     * optional assignment status (all three unset = every assignment, in stable id order).
     */
    @EntityGraph(attributePaths = {"user", "user.role", "store"})
    @Query("select a from StaffStoreAssignment a where (:storeId is null or a.store.id = :storeId)"
            + " and (:like = '' or lower(a.user.fullName) like :like escape '!' or lower(a.user.email) like :like escape '!')"
            + " and (:status is null or a.status = :status)"
            + " order by a.id")
    Page<StaffStoreAssignment> searchAdmin(@Param("storeId") Long storeId, @Param("like") String like,
                                           @Param("status") ActiveStatus status, Pageable pageable);

    @Query("""
            select count(a) from StaffStoreAssignment a where a.store.id = :storeId
            and a.status = com.oneshop.entity.ActiveStatus.ACTIVE
            and a.user.status = com.oneshop.entity.ActiveStatus.ACTIVE
            and a.user.role.name = com.oneshop.entity.RoleName.STAFF
            and a.store.status = com.oneshop.entity.ActiveStatus.ACTIVE""")
    long countEffectiveStaff(@Param("storeId") Long storeId);

    /**
     * The Store scope of a Staff account (BR-14): Stores with an ACTIVE assignment to this ACTIVE STAFF user. An
     * INACTIVE assignment, an INACTIVE Store or an account that is no longer STAFF gives no scope.
     */
    @Query("""
            select a.store from StaffStoreAssignment a
            where a.user.email = :email
              and a.user.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and a.user.role.name = com.oneshop.entity.RoleName.STAFF
              and a.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and a.store.status = com.oneshop.entity.ActiveStatus.ACTIVE
            order by a.store.name""")
    List<Store> findActiveStoresByStaffEmail(@Param("email") String email);

    /** The STAFF accounts with an ACTIVE assignment in the given Stores — the recorder filter of the central log.
     *  No SQL DISTINCT: SQL Server rejects ORDER BY items outside the select list under DISTINCT; the caller dedupes. */
    @Query("""
            select a.user from StaffStoreAssignment a
            where a.store.id in :storeIds
              and a.status = com.oneshop.entity.ActiveStatus.ACTIVE
              and a.user.role.name = com.oneshop.entity.RoleName.STAFF
            order by lower(a.user.fullName)""")
    List<com.oneshop.entity.User> findActiveStaffOfStores(@Param("storeIds") Collection<Long> storeIds);
}
