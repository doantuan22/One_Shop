package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.StaffStoreAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffStoreAssignmentRepository extends JpaRepository<StaffStoreAssignment, Long> {

    /** Store scope of a Staff: the Store comes from here, never from a request parameter (BR-14). */
    @EntityGraph(attributePaths = "store")
    List<StaffStoreAssignment> findByUserIdAndStatus(Long userId, ActiveStatus status);

    boolean existsByUserIdAndStoreIdAndStatus(Long userId, Long storeId, ActiveStatus status);
}
