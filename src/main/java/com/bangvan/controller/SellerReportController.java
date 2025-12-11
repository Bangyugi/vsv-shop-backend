package com.bangvan.controller;


import com.bangvan.dto.response.ApiResponse;
import com.bangvan.service.SellerReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Seller Report", description = "Seller Report Management API")
public class SellerReportController {

    private final SellerReportService sellerReportService;

    @GetMapping("/seller/my-report")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Get current seller's report (Basic)", description = "Endpoint for a seller to retrieve their sales and earnings report (Snapshot data).")
    public ResponseEntity<ApiResponse> getMySellerReport(Principal principal) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Seller report fetched successfully",
                sellerReportService.getMySellerReport(principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/seller/dashboard")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Get Seller Dashboard Data", description = "Get real-time metrics for seller dashboard (Revenue, Orders, Stock, Chart).")
    public ResponseEntity<ApiResponse> getSellerDashboard(Principal principal) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Dashboard data fetched successfully",
                sellerReportService.getSellerDashboard(principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a seller's report by ID", description = "Endpoint for an admin to retrieve any seller's report.")
    public ResponseEntity<ApiResponse> getSellerReportById(@PathVariable Long sellerId) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Seller report fetched successfully",
                sellerReportService.getReportBySellerId(sellerId)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PostMapping("/seller/{sellerId}/generate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generate/Update a seller's report", description = "Endpoint for an admin to manually trigger the calculation and update of a seller's report.")
    public ResponseEntity<ApiResponse> generateSellerReport(@PathVariable Long sellerId) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Seller report generated successfully",
                sellerReportService.generateSellerReport(sellerId)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}