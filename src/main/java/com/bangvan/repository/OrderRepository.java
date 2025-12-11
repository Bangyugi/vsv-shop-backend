package com.bangvan.repository;

import com.bangvan.entity.Order;
import com.bangvan.entity.Seller;
import com.bangvan.entity.User;
import com.bangvan.utils.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUser(User user, Pageable pageable);

    Optional<Order> findByOrderId(String orderId);

    Page<Order> findBySeller(Seller seller, Pageable pageable);

    List<Order> findBySeller(Seller seller);

    @Query("SELECT o FROM Order o WHERE o.user = :user AND o.orderStatus != 'DELIVERED'")
    List<Order> findByUserAndOrderStatusNotDelivered(User user);

    Page<Order> findByUserAndOrderStatus(User user, OrderStatus orderStatus, Pageable pageable);

    List<Order> findAllByUser(User user);




    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.seller.id = :sellerId AND o.orderStatus = 'DELIVERED'")
    BigDecimal calculateTotalRevenueBySeller(@Param("sellerId") Long sellerId);


    @Query("SELECT COUNT(o) FROM Order o WHERE o.seller.id = :sellerId AND o.orderDate >= :startDate")
    Integer countNewOrdersBySeller(@Param("sellerId") Long sellerId, @Param("startDate") LocalDateTime startDate);


    @Query("SELECT COUNT(o) FROM Order o WHERE o.seller.id = :sellerId AND o.orderStatus IN ('PENDING', 'PROCESSING')")
    Integer countPendingOrdersBySeller(@Param("sellerId") Long sellerId);



    @Query("SELECT EXTRACT(MONTH FROM o.orderDate) as month, EXTRACT(YEAR FROM o.orderDate) as year, SUM(o.totalPrice) " +
            "FROM Order o " +
            "WHERE o.seller.id = :sellerId AND o.orderStatus = 'DELIVERED' AND o.orderDate >= :startDate " +
            "GROUP BY EXTRACT(MONTH FROM o.orderDate), EXTRACT(YEAR FROM o.orderDate) " +
            "ORDER BY year ASC, month ASC")
    List<Object[]> findMonthlyRevenueBySeller(@Param("sellerId") Long sellerId, @Param("startDate") LocalDateTime startDate);
}