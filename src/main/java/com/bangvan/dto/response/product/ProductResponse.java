package com.bangvan.dto.response.product;

import com.bangvan.dto.response.category.CategoryResponse;
import com.bangvan.dto.response.seller.SellerResponse;
// Xóa import Entity cũ: import com.bangvan.entity.ProductVariant;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductResponse {
    Long id;
    String title;
    String description;
    BigDecimal price;
    BigDecimal sellingPrice;
    Integer discountPercent;
    List<String> images;
    Integer numRatings;

    SellerResponse seller;
    CategoryResponse category;


    Set<ProductVariantResponse> variants = new HashSet<>();

    Double averageRating;
    Integer totalQuantity;
    Integer totalSold;
}