package com.oneshop.repository;

import com.oneshop.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /** The cart of the authenticated customer, identified by the e-mail of the security principal. */
    Optional<Cart> findByUserEmail(String email);

    /**
     * Same cart, locked until the transaction ends, so two requests of one customer change it one after the other
     * (for example two "add to cart" clicks merging into the same line). This serializes a customer with themselves
     * only; it reserves no stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.email = :email")
    Optional<Cart> findByUserEmailForUpdate(@Param("email") String email);
}
