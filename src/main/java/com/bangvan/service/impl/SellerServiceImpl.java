package com.bangvan.service.impl;

import com.bangvan.dto.request.seller.BecomeSellerRequest;
import com.bangvan.dto.request.seller.UpdateSellerRequest;
import com.bangvan.dto.response.PageCustomResponse;
import com.bangvan.dto.response.seller.SellerResponse;
import com.bangvan.dto.response.seller.UpdateSellerStatusRequest;
import com.bangvan.dto.response.user.UserResponse;
import com.bangvan.entity.Address;
import com.bangvan.entity.Role;
import com.bangvan.entity.Seller;
import com.bangvan.entity.User;
import com.bangvan.exception.AppException;
import com.bangvan.exception.ErrorCode;
import com.bangvan.exception.ResourceNotFoundException;
import com.bangvan.repository.AddressRepository;
import com.bangvan.repository.RoleRepository;
import com.bangvan.repository.SellerRepository;
import com.bangvan.repository.UserRepository;
import com.bangvan.service.SellerService;
import com.bangvan.utils.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerServiceImpl implements SellerService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final ModelMapper modelMapper;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;

    private SellerResponse mapSellerToSellerResponse(Seller seller) {
        SellerResponse sellerResponse = modelMapper.map(seller, SellerResponse.class);
        UserResponse userResponse = modelMapper.map(seller.getUser(), UserResponse.class);
        sellerResponse.setUser(userResponse);
        return sellerResponse;
    }

    @Override
    public SellerResponse getProfile(Principal principal){
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("seller", "username", username));
        return mapSellerToSellerResponse(seller);
    }

    @Transactional
    @Override
    public SellerResponse becomeSeller(BecomeSellerRequest request, Principal principal){
        String username = principal.getName();
        User user = userRepository.findByUsernameAndEnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("user", "username", username));

        if(sellerRepository.existsById(user.getId())){
            throw new AppException(ErrorCode.SELLER_EXISTED);
        }

        Seller seller = new Seller();
        seller.setUser(user);
        seller.setBusinessDetails(request.getBusinessDetails());
        seller.setBankDetails(request.getBankDetails());
        seller.setGstin(request.getGstin());
        seller.setAccountStatus(AccountStatus.PENDING_VERIFICATION);


        Address pickupAddress = request.getPickupAddress();
        pickupAddress.setUser(user);
        Address managedPickupAddress = addressRepository.save(pickupAddress);
        seller.setPickupAddress(managedPickupAddress);




        seller = sellerRepository.save(seller);
        log.info("User {} registered as seller. Status: PENDING_VERIFICATION", username);

        return mapSellerToSellerResponse(seller);
    }

    @Transactional
    @Override
    public SellerResponse updateSeller(UpdateSellerRequest request, Principal principal){
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("seller", "username", username));

        Address pickupAddress = request.getPickupAddress();
        pickupAddress.setUser(seller.getUser());
        Address managedPickupAddress = addressRepository.save(pickupAddress);

        seller.setBusinessDetails(request.getBusinessDetails());
        seller.setBankDetails(request.getBankDetails());
        seller.setPickupAddress(managedPickupAddress);
        seller.setGstin(request.getGstin());

        Seller updatedSeller = sellerRepository.save(seller);
        return mapSellerToSellerResponse(updatedSeller);
    }

    @Transactional
    @Override
    public String deleteSeller(Principal principal){
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("seller", "username", username));


        User user = seller.getUser();
        Role sellerRole = roleRepository.findByName("ROLE_SELLER")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        if (user.getRoles().contains(sellerRole)) {
            user.getRoles().remove(sellerRole);
            userRepository.save(user);
        }

        sellerRepository.delete(seller);
        return "Delete seller successfully";
    }

    @Override
    public PageCustomResponse<SellerResponse> getAllSellers(Pageable pageable){
        Page<Seller> page = sellerRepository.findAll(pageable);
        return mapToPageResponse(page);
    }

    @Override
    public PageCustomResponse<SellerResponse> getPendingSellers(Pageable pageable) {
        Page<Seller> page = sellerRepository.findByAccountStatus(AccountStatus.PENDING_VERIFICATION, pageable);
        return mapToPageResponse(page);
    }

    @Transactional(readOnly = true)
    @Override
    public SellerResponse findSellerById(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "ID", sellerId));
        return mapSellerToSellerResponse(seller);
    }

    @Transactional
    @Override
    public SellerResponse updateSellerStatus(Long sellerId, UpdateSellerStatusRequest request) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "ID", sellerId));

        User user = seller.getUser();
        AccountStatus newStatus;
        try {
            newStatus = AccountStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status value provided: {}", request.getStatus());
            throw new AppException(ErrorCode.INVALID_INPUT, "Invalid status value: " + request.getStatus());
        }


        seller.setAccountStatus(newStatus);


        Role sellerRole = roleRepository.findByName("ROLE_SELLER")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        Set<Role> userRoles = user.getRoles();

        if (newStatus == AccountStatus.ACTIVE) {

            if (!userRoles.contains(sellerRole)) {
                userRoles.add(sellerRole);
                log.info("Granted ROLE_SELLER to user {}", user.getUsername());
            }
            user.setEnabled(true);
        } else if (newStatus == AccountStatus.BANNED || newStatus == AccountStatus.DEACTIVATED || newStatus == AccountStatus.CLOSED) {

            if (userRoles.contains(sellerRole)) {
                userRoles.remove(sellerRole);
                log.info("Revoked ROLE_SELLER from user {}", user.getUsername());
            }
            if (newStatus == AccountStatus.BANNED) {
                user.setEnabled(false);
            }
        }


        user.setAccountStatus(newStatus);
        user.setRoles(userRoles);

        userRepository.save(user);
        Seller updatedSeller = sellerRepository.save(seller);

        return mapSellerToSellerResponse(updatedSeller);
    }

    private PageCustomResponse<SellerResponse> mapToPageResponse(Page<Seller> page) {
        return PageCustomResponse.<SellerResponse>builder()
                .pageNo(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .pageContent(page.getContent().stream().map(this::mapSellerToSellerResponse).toList())
                .build();
    }
}