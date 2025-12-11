package com.bangvan.controller;

import com.bangvan.dto.request.user.AddressRequest;
import com.bangvan.dto.response.ApiResponse;
import com.bangvan.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@Tag(name = "Address", description = "User Address Management API")
@Slf4j
@PreAuthorize("isAuthenticated()")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    @Operation(summary = "Add a new address", description = "Endpoint for the logged-in user to add a new address.")
    public ResponseEntity<ApiResponse> addAddress(@Valid @RequestBody AddressRequest request, Principal principal) {
        log.info("Received request to add address for user: {}", principal.getName());
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.CREATED.value(),
                "Address added successfully",
                addressService.addAddress(request, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all addresses", description = "Endpoint for the logged-in user to retrieve all their addresses.")
    public ResponseEntity<ApiResponse> getMyAddresses(Principal principal) {
        log.info("Received request to get addresses for user: {}", principal.getName());
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Addresses retrieved successfully",
                addressService.getMyAddresses(principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Get address by ID", description = "Endpoint for the logged-in user to retrieve a specific address by its ID.")
    public ResponseEntity<ApiResponse> getAddressById(@PathVariable Long addressId, Principal principal) {
        log.info("Received request to get address with ID: {} for user: {}", addressId, principal.getName());
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Address found successfully",
                addressService.getAddressById(addressId, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Update an address", description = "Endpoint for the logged-in user to update their existing address.")
    public ResponseEntity<ApiResponse> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request,
            Principal principal) {
        log.info("Received request to update address with ID: {} for user: {}", addressId, principal.getName());
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Address updated successfully",
                addressService.updateAddress(addressId, request, principal)
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete an address", description = "Endpoint for the logged-in user to delete their address.")
    public ResponseEntity<ApiResponse> deleteAddress(@PathVariable Long addressId, Principal principal) {
        log.info("Received request to delete address with ID: {} for user: {}", addressId, principal.getName());
        addressService.deleteAddress(addressId, principal);
        ApiResponse apiResponse = ApiResponse.success(
                HttpStatus.OK.value(),
                "Address deleted successfully",
                null
        );
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}