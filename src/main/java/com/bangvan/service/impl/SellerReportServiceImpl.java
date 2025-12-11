package com.bangvan.service.impl;

import com.bangvan.dto.response.seller.MonthlySalesResponse;
import com.bangvan.dto.response.seller.SellerDashboardResponse;
import com.bangvan.service.SellerReportService;
import com.bangvan.repository.ProductVariantRepository; // Import mới
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Thêm Log
import org.springframework.stereotype.Service;


import com.bangvan.dto.response.seller.SellerReportResponse;
import com.bangvan.entity.Order;
import com.bangvan.entity.Seller;
import com.bangvan.entity.SellerReport;
import com.bangvan.exception.ResourceNotFoundException;
import com.bangvan.repository.OrderRepository;
import com.bangvan.repository.SellerRepository;
import com.bangvan.repository.SellerReportRepository;
import com.bangvan.utils.OrderStatus;
import org.modelmapper.ModelMapper;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerReportServiceImpl implements SellerReportService {

    private final SellerReportRepository sellerReportRepository;
    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository; // Inject thêm
    private final ModelMapper modelMapper;

    @Override
    public SellerReportResponse getMySellerReport(Principal principal) {
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

        return getOrCreateReport(seller.getId());
    }

    @Override
    public SellerReportResponse getReportBySellerId(Long sellerId) {
        return getOrCreateReport(sellerId);
    }

    private SellerReportResponse getOrCreateReport(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "ID", sellerId));

        SellerReport report = sellerReportRepository.findBySellerId(sellerId)
                .orElseGet(() -> createInitialReport(seller));

        SellerReportResponse response = modelMapper.map(report, SellerReportResponse.class);
        response.setSellerId(seller.getId());
        return response;
    }

    private SellerReport createInitialReport(Seller seller) {
        SellerReport newReport = new SellerReport();
        newReport.setSeller(seller);
        return sellerReportRepository.save(newReport);
    }

    @Transactional
    @Override
    public SellerReportResponse generateSellerReport(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "ID", sellerId));

        List<Order> orders = orderRepository.findBySeller(seller);

        SellerReport report = sellerReportRepository.findBySellerId(sellerId)
                .orElseGet(() -> createInitialReport(seller));

        BigDecimal totalSales = BigDecimal.ZERO;
        int totalOrders = 0;
        int canceledOrders = 0;

        for (Order order : orders) {
            if (order.getOrderStatus() == OrderStatus.DELIVERED) {
                totalSales = totalSales.add(order.getTotalPrice());
                totalOrders++;
            } else if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                canceledOrders++;
            }
        }
        report.setTotalSales(totalSales);
        report.setTotalOrders(totalOrders);
        report.setCanceledOrders(canceledOrders);

        report.setNetEarnings(totalSales);
        report.setTotalEarnings(totalSales);

        SellerReport updatedReport = sellerReportRepository.save(report);

        SellerReportResponse response = modelMapper.map(updatedReport, SellerReportResponse.class);
        response.setSellerId(seller.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SellerDashboardResponse getSellerDashboard(Principal principal) {
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));
        Long sellerId = seller.getId();


        BigDecimal totalRevenue = orderRepository.calculateTotalRevenueBySeller(sellerId);


        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Integer newOrders = orderRepository.countNewOrdersBySeller(sellerId, startOfMonth);


        Integer productsInStock = productVariantRepository.sumStockBySeller(sellerId);


        Integer pendingOrders = orderRepository.countPendingOrdersBySeller(sellerId);


        List<MonthlySalesResponse> chartData = getRevenueAnalytics(sellerId);

        return SellerDashboardResponse.builder()
                .totalRevenue(totalRevenue)
                .newOrders(newOrders)
                .productsInStock(productsInStock)
                .pendingOrders(pendingOrders)
                .revenueAnalytics(chartData)
                .build();
    }

    private List<MonthlySalesResponse> getRevenueAnalytics(Long sellerId) {

        LocalDateTime sixMonthsAgo = LocalDate.now().minusMonths(5).withDayOfMonth(1).atStartOfDay();
        List<Object[]> rawData = orderRepository.findMonthlyRevenueBySeller(sellerId, sixMonthsAgo);


        Map<String, BigDecimal> revenueMap = new HashMap<>();
        for (Object[] row : rawData) {

            int month = ((Number) row[0]).intValue();
            int year = ((Number) row[1]).intValue();
            BigDecimal revenue = (BigDecimal) row[2];

            String key = String.format("%d-%02d", year, month);
            revenueMap.put(key, revenue);
        }


        List<MonthlySalesResponse> result = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();


        for (int i = 5; i >= 0; i--) {
            YearMonth targetMonth = currentMonth.minusMonths(i);
            String key = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));


            String displayMonth = targetMonth.format(DateTimeFormatter.ofPattern("MMM")); // Tiếng Anh: Jan, Feb...

            BigDecimal revenue = revenueMap.getOrDefault(key, BigDecimal.ZERO);

            result.add(MonthlySalesResponse.builder()
                    .month(displayMonth)
                    .revenue(revenue)
                    .build());
        }

        return result;
    }
}