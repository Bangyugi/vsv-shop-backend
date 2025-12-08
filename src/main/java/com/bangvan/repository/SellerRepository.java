package com.bangvan.repository;

import com.bangvan.entity.Seller;
import com.bangvan.entity.SellerReport;
import com.bangvan.entity.User;
import com.bangvan.utils.AccountStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findByUser_UsernameAndUser_EnabledIsTrue(String username);


    Page<Seller> findByUser_EnabledIsTrue(Pageable pageable);

    Page<Seller> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);
}
