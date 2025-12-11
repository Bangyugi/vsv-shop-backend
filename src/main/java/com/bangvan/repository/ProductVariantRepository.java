package com.bangvan.repository;

import com.bangvan.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductVariant> findById(Long id);


    @Query("SELECT COALESCE(SUM(pv.quantity), 0) FROM ProductVariant pv JOIN pv.product p WHERE p.seller.id = :sellerId")
    Integer sumStockBySeller(@Param("sellerId") Long sellerId);
}