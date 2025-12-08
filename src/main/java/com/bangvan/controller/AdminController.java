package com.bangvan.controller;

import com.bangvan.dto.response.ApiResponse;
import com.bangvan.dto.response.seller.UpdateSellerStatusRequest;
import com.bangvan.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Admin Management API")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final SellerService sellerService;
    private final OrderService orderService;

    @GetMapping("/users")
    @Operation(summary = "Get All Users", description = "Endpoint for admins to get a paginated list of all users.")
    public ResponseEntity<ApiResponse> getAllUsers(
            @RequestParam(value = "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdAt", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "ASC", required = false) String sortDir
    ) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Users fetched successfully",
                userService.findAllUsers(pageable)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/orders")
    @Operation(summary = "Get All Orders", description = "Endpoint for admins to get a paginated list of all orders in the system.")
    public ResponseEntity<ApiResponse> getAllOrders(
            @RequestParam(value = "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "orderDate", required = false) String sortBy,
            @RequestParam(value="sortDir", defaultValue = "DESC", required = false) String sortDir
    ) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "All orders fetched successfully",orderService.findAllOrders(pageable)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @Operation(summary = "Delete User", description = "Delete User")
    @DeleteMapping("users/delete/{userId}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long userId){
        ApiResponse apiResponse = ApiResponse.success(200, "User deleted successfully", userService.deleteUser(userId));
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @Operation(summary = "Find User By Id", description = "Find User By Id")
    @GetMapping("users/find/{userId}")
    public ResponseEntity<ApiResponse> findUserById(@PathVariable Long userId){
        ApiResponse apiResponse = ApiResponse.success(200, "User found successfully", userService.findUserById(userId));
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/sellers")
    @Operation(summary = "Get All Sellers", description = "Endpoint for admins to get a paginated list of all sellers.")
    public ResponseEntity<ApiResponse> getAllSellers(
            @RequestParam(value= "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdAt", required = false) String sortBy,
            @RequestParam(value="sortDir", defaultValue = "ASC", required = false) String sortDir
    ) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Sellers fetched successfully",
                sellerService.getAllSellers(pageable)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }


    @GetMapping("/sellers/pending")
    @Operation(summary = "Get Pending Sellers", description = "Get list of sellers waiting for approval (PENDING_VERIFICATION).")
    public ResponseEntity<ApiResponse> getPendingSellers(
            @RequestParam(value= "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdAt", required = false) String sortBy,
            @RequestParam(value="sortDir", defaultValue = "ASC", required = false) String sortDir
    ) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Pending sellers fetched successfully",
                sellerService.getPendingSellers(pageable)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }


    @GetMapping("/sellers/{sellerId}")
    @Operation(summary = "Find Seller By Id", description = "Endpoint for admins to find a specific seller by their ID.")
    public ResponseEntity<ApiResponse> findSellerById(@PathVariable Long sellerId){
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Seller found successfully",
                sellerService.findSellerById(sellerId)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PutMapping("/sellers/{sellerId}/status")
    @Operation(summary = "Approve/Reject Seller", description = "Update seller status to ACTIVE (Approve) or DEACTIVATED/BANNED (Reject).")
    public ResponseEntity<ApiResponse> updateSellerStatus(
            @PathVariable Long sellerId,
            @Valid @RequestBody UpdateSellerStatusRequest request) {
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Seller status has been updated successfully",
                sellerService.updateSellerStatus(sellerId, request)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}