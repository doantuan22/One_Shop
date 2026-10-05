package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** A Store the Client may select: it must exist and be ACTIVE. */
    Optional<Store> findByIdAndStatus(Long id, ActiveStatus status);

    List<Store> findByStatusOrderByProvinceCityAscAreaAscNameAsc(ActiveStatus status);

    /** Every Store whatever its status (Store Finder, Admin). */
    List<Store> findAllByOrderByProvinceCityAscAreaAscNameAsc();

    /** Store Finder filter by province/city and area. */
    List<Store> findByStatusAndProvinceCityAndAreaOrderByName(ActiveStatus status, String provinceCity, String area);
}
