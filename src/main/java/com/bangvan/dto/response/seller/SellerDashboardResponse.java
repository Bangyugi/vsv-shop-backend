package com.bangvan.dto.response.seller;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SellerDashboardResponse {
    BigDecimal totalRevenue;   
    Integer newOrders;         
    Integer productsInStock;   
    Integer pendingOrders;     
    List<MonthlySalesResponse> revenueAnalytics;
}