package com.oneshop.repository;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Admin store list search: contains-match over code / name / address / area, optional area value and optional
     * status (all three empty/unset return every Store whatever its status, in the stable finder ordering).
     */
    @Query("select s from Store s where (:like = '' or lower(s.code) like :like escape '!'"
            + " or lower(s.name) like :like escape '!' or lower(s.address) like :like escape '!'"
            + " or lower(s.area) like :like escape '!' or lower(s.provinceCity) like :like escape '!')"
            + " and (:area = '' or s.area = :area)"
            + " and (:status is null or s.status = :status)"
            + " order by s.provinceCity, s.area, s.name, s.id")
    Page<Store> searchAdmin(@Param("like") String like, @Param("area") String area,
                            @Param("status") ActiveStatus status, Pageable pageable);
}
