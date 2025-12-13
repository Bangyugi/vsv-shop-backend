package com.bangvan.dto.response.product;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariantResponse {
    Long id;
    String color;
    String size;
    Integer quantity;
    Integer sold;
    String sku;
}