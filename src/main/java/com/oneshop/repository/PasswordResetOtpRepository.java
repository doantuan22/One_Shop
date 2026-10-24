package com.oneshop.repository;

import com.oneshop.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /** The newest code of this e-mail that is still unused (it may be expired: the caller checks). */
    Optional<PasswordResetOtp> findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(String email);

    /** The newest code ever sent to this e-mail, used or not (resend cool-down). */
    Optional<PasswordResetOtp> findFirstByEmailOrderByIdDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime since);

    /** Kills every still-open code of this e-mail, so only the newest code can ever be used. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetOtp o set o.consumedAt = :now where o.email = :email and o.consumedAt is null")
    int consumeAllOpen(@Param("email") String email, @Param("now") LocalDateTime now);

    /** Uses one code exactly once: 1 when this call used it, 0 when someone else already did. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetOtp o set o.consumedAt = :now where o.id = :id and o.consumedAt is null")
    int consume(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetOtp o set o.attempts = o.attempts + 1 where o.id = :id")
    int addAttempt(@Param("id") Long id);
}
