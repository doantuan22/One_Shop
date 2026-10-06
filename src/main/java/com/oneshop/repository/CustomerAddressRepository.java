package com.oneshop.repository;

import com.oneshop.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);

    /** Addresses of the authenticated customer, default first. */
    List<CustomerAddress> findByUserEmailOrderByDefaultAddressDescCreatedAtDesc(String email);
}
