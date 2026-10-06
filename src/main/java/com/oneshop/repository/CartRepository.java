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
     * The cart of one user, locked until the transaction ends, so two requests of one customer change it one after
     * the other (for example two "add to cart" clicks merging into the same line, or a double "place order").
     *
     * <p>It filters on {@code user_id} alone so that SQL Server reaches the row through the unique index
     * UQ_carts_user and locks that single row. Filtering through a join on the e-mail made it scan the table under
     * the update lock, which made every customer wait for every other customer. This lock serializes a customer
     * with themselves only; it reserves no stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
