package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCode(String code);

    List<Store> findByStatusOrderByProvinceCityAscAreaAscNameAsc(ActiveStatus status);

    /** Store Finder filter by province/city and area. */
    List<Store> findByStatusAndProvinceCityAndAreaOrderByName(ActiveStatus status, String provinceCity, String area);
}
