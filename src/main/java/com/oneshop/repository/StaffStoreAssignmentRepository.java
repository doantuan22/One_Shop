package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.StaffStoreAssignment;
import com.oneshop.entity.Store;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StaffStoreAssignmentRepository extends JpaRepository<StaffStoreAssignment, Long> {

    /** Store scope of a Staff: the Store comes from here, never from a request parameter (BR-14). */
    @EntityGraph(attributePaths = "store")
    List<StaffStoreAssignment> findByUserIdAndStatus(Long userId, ActiveStatus status);

    boolean existsByUserIdAndStoreIdAndStatus(Long userId, Long storeId, ActiveStatus status);

    Optional<StaffStoreAssignment> findByUserIdAndStoreId(Long userId, Long storeId);

    List<StaffStoreAssignment> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "user.role", "store"})
    @Query("select a from StaffStoreAssignment a where (:storeId is null or a.store.id = :storeId) order by a.id")
    Page<StaffStoreAssignment> searchAdmin(@Param("storeId") Long storeId, Pageable pageable);

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
}
