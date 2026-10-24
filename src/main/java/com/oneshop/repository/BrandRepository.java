package com.oneshop.repository;

import com.oneshop.entity.Brand;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Brand b where b.id=:id")
    java.util.Optional<Brand> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Brand> findByStatusOrderByName(VisibilityStatus status);

    List<Brand> findAllByOrderByName();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
