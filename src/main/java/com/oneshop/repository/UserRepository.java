package com.oneshop.repository;

import com.oneshop.entity.User;
import com.oneshop.entity.RoleName;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByUserNameIgnoreCase(String userName);

    boolean existsByUserNameIgnoreCase(String userName);

    List<User> findByRoleNameOrderByFullNameAscIdAsc(RoleName role);

    @Query("select u from User u where (:role is null or u.role.name = :role) order by u.id")
    Page<User> searchAdmin(@Param("role") RoleName role, Pageable pageable);

    /**
     * Admin user console search: optional role, optional account status and a contains-match over
     * full name / e-mail / user name (all three unset = every account in stable id order).
     */
    @Query("select u from User u where (:role is null or u.role.name = :role)"
            + " and (:status is null or u.status = :status)"
            + " and (:like = '' or lower(u.fullName) like :like escape '!' or lower(u.email) like :like escape '!'"
            + " or (u.userName is not null and lower(u.userName) like :like escape '!'))"
            + " order by u.id")
    Page<User> searchAdmin(@Param("role") RoleName role, @Param("status") com.oneshop.entity.ActiveStatus status,
                           @Param("like") String like, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
