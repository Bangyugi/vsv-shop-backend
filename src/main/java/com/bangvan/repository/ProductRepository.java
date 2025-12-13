package com.bangvan.repository;

import com.bangvan.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Iterable<Product> findByCategoryId(Long categoryId);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    // Phương thức này có thể xóa nếu không dùng, vì đã có searchProducts bên dưới mạnh mẽ hơn
    Page<Product> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    // Đây là phương thức thay thế cho Elasticsearch
    @Query("SELECT p FROM Product p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> searchProducts(@Param("keyword") String keyword, Pageable pageable);
}