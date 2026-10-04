package com.oneshop.repository;

import com.oneshop.entity.Brand;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    List<Brand> findByStatusOrderByName(VisibilityStatus status);
}
