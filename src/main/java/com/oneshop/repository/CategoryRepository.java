package com.oneshop.repository;

import com.oneshop.entity.Category;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Category c where c.id=:id")
    java.util.Optional<Category> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Category> findByStatusOrderByName(VisibilityStatus status);

    List<Category> findAllByOrderByName();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
