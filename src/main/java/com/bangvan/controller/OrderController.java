package com.bangvan.controller;
import com.bangvan.dto.request.order.CreateOrderRequest;
import com.bangvan.dto.request.order.UpdateOrderStatusRequest;
import com.bangvan.dto.response.ApiResponse;
import com.bangvan.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "Order Management API")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create an order", description = "Endpoint to create a new order from the user's cart")
    public ResponseEntity<ApiResponse> createOrder(@Valid @RequestBody CreateOrderRequest request, Principal principal) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.CREATED.value(),
                "Order created successfully",
                orderService.createOrder(request, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Find orders by user", description = "Endpoint for users to view their order history with pagination")
    public ResponseEntity<ApiResponse> findOrderByUser(
            Principal principal,
            @RequestParam(value = "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "orderDate", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC", required = false) String sortDir
    ) {

        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDir), sortBy));

        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Orders fetched successfully",
                orderService.findOrderByUser(principal, pageable)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Find order by ID", description = "Endpoint for a user (buyer), seller, or admin to find a specific order by its ID.")
    public ResponseEntity<ApiResponse> findOrderById(@PathVariable Long orderId, Principal principal) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Order found successfully",
                orderService.findOrderById(orderId, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/uuid/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Find order by OrderId (String UUID)", description = "Endpoint for a user (buyer), seller, or admin to find a specific order by its public String UUID (orderId).")
    public ResponseEntity<ApiResponse> findOrderByOrderIdString(@PathVariable String orderId, Principal principal) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Order found successfully",
                orderService.findOrderByOrderIdString(orderId, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Get orders for the current seller", description = "Endpoint for sellers to view their orders with pagination")
    public ResponseEntity<ApiResponse> getSellerOrders(
            Principal principal,
            @RequestParam(value = "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "orderDate", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC", required = false) String sortDir
    ) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Seller orders fetched successfully",
                orderService.getSellerOrders(principal, pageable)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    @Operation(summary = "Update an order's status", description = "Endpoint for sellers or admins to update the status of an order")
    public ResponseEntity<ApiResponse> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateOrderStatusRequest request,
            Principal principal
    ) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Order status updated successfully",
                orderService.updateOrderStatus(orderId, request.getStatus(), principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an order", description = "Endpoint for admins to permanently delete an order")
    public ResponseEntity<ApiResponse> deleteOrder(@PathVariable Long orderId) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                orderService.deleteOrder(orderId),
                null
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
    @PatchMapping("/uuid/{orderId}/cancel")
    @Operation(summary = "Cancel an order by OrderId (String UUID)", description = "Endpoint for users to cancel their own order using the String orderId.")
    public ResponseEntity<ApiResponse> cancelOrder(
            @PathVariable String orderId,
            Principal principal
    ) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Order cancelled successfully",
                orderService.cancelOrder(orderId, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
