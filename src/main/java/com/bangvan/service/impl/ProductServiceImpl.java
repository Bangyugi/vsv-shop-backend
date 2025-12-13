package com.bangvan.service.impl;

import com.bangvan.dto.request.product.CreateProductRequest;
import com.bangvan.dto.request.product.UpdateProductRequest;
import com.bangvan.dto.request.product.UpdateStockRequest;
import com.bangvan.dto.response.PageCustomResponse;
import com.bangvan.dto.response.product.ProductResponse;
import com.bangvan.entity.*;
import com.bangvan.exception.AppException;
import com.bangvan.exception.ErrorCode;
import com.bangvan.exception.ResourceNotFoundException;
import com.bangvan.repository.*;
import com.bangvan.service.ProductService;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final SellerRepository sellerRepository;
    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "title", "price", "sellingPrice", "createdAt", "updatedAt", "averageRating", "sold");
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    @Override
    public ProductResponse createProduct(CreateProductRequest request, Principal principal) {
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "categoryId", request.getCategoryId()));

        Product product = modelMapper.map(request, Product.class);
        product.setSeller(seller);
        product.setCategory(category);
        product.setVariants(new HashSet<>());

        for (ProductVariant variantRequest : request.getVariants()) {
            ProductVariant newVariant = new ProductVariant();
            newVariant.setColor(variantRequest.getColor());
            newVariant.setSize(variantRequest.getSize());
            newVariant.setQuantity(variantRequest.getQuantity());

            newVariant.setSold(0);
            String sku = (product.getTitle() + "-" + variantRequest.getColor() + "-" + variantRequest.getSize())
                    .replaceAll("\\s+", "-").toUpperCase();
            newVariant.setSku(sku);
            newVariant.setProduct(product);
            product.getVariants().add(newVariant);
        }

        Product savedProduct = productRepository.save(product);
        return mapProductToResponse(savedProduct);
    }

    private ProductResponse mapProductToResponse(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setTotalQuantity(product.getTotalQuantity());
        response.setTotalSold(product.getTotalSold());
        return response;
    }

    @Override
    @Cacheable(value = "productDetails", key = "#productId")
    public ProductResponse getProductById(Long productId) {
        // Log để kiểm tra xem request có hit vào database hay không (Check cache miss)
        log.info("Fetching product from Database with ID: {}", productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        return mapProductToResponseWithRating(product);
    }

    @Override
    public PageCustomResponse<ProductResponse> getAllProducts(
            String keyword, Long categoryId, Long sellerId,
            BigDecimal minPrice, BigDecimal maxPrice, String color, String size,
            Double minRating,
            Pageable pageable) {

        Specification<Product> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isEmpty()) {
                String keywordLower = "%" + keyword.toLowerCase() + "%";
                Predicate titleLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), keywordLower);
                Predicate descriptionLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), keywordLower);
                predicates.add(criteriaBuilder.or(titleLike, descriptionLike));
            }

            if (categoryId != null) {
                Set<Long> categoryIdsToFilter = getAllCategoryIdsIncludingChildren(categoryId);
                if (!categoryIdsToFilter.isEmpty()) {
                    predicates.add(root.get("category").get("id").in(categoryIdsToFilter));
                } else {
                    log.warn("Category ID {} provided but no categories found (including children). No category filter applied.", categoryId);
                }
            }

            if (sellerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("seller").get("id"), sellerId));
            }

            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("sellingPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("sellingPrice"), maxPrice));
            }

            boolean needsDistinct = false;
            if (color != null && !color.isEmpty() || size != null && !size.isEmpty()) {
                Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);
                if (color != null && !color.isEmpty()) {
                    predicates.add(criteriaBuilder.equal(variantJoin.get("color"), color));
                }
                if (size != null && !size.isEmpty()) {
                    if ("One Size".equalsIgnoreCase(size)) {
                        predicates.add(criteriaBuilder.equal(variantJoin.get("size"), "One Size"));
                    } else {
                        predicates.add(criteriaBuilder.equal(variantJoin.get("size"), size));
                    }
                }
                needsDistinct = true;
            }

            if (minRating != null && minRating > 0) {
                Subquery<Double> avgRatingSubquery = query.subquery(Double.class);
                Root<Review> reviewRootSub = avgRatingSubquery.from(Review.class);
                Join<Review, OrderItem> orderItemJoin = reviewRootSub.join("orderItem");
                Join<OrderItem, ProductVariant> variantJoin = orderItemJoin.join("variant");
                avgRatingSubquery.select(criteriaBuilder.avg(reviewRootSub.get("rating")))
                        .where(criteriaBuilder.equal(variantJoin.get("product"), root));

                predicates.add(criteriaBuilder.greaterThanOrEqualTo(avgRatingSubquery, minRating));
            }

            if (needsDistinct) {
                query.distinct(true);
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> productPage = productRepository.findAll(spec, pageable);

        List<ProductResponse> productResponses = productPage.getContent().stream()
                .map(this::mapProductToResponseWithRating)
                .collect(Collectors.toList());

        return PageCustomResponse.<ProductResponse>builder()
                .pageNo(productPage.getNumber() + 1)
                .pageSize(productPage.getSize())
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .pageContent(productResponses)
                .build();
    }

    private Set<Long> getAllCategoryIdsIncludingChildren(Long categoryId) {
        Set<Long> collectedCategoryIds = new HashSet<>();
        if (categoryId == null) {
            return collectedCategoryIds;
        }

        List<Category> allCategories = categoryRepository.findAll();
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .filter(cat -> cat.getParentCategory() != null)
                .collect(Collectors.groupingBy(cat -> cat.getParentCategory().getId()));

        Queue<Category> categoriesToProcess = new LinkedList<>();

        Category initialCategory = allCategories.stream()
                .filter(cat -> cat.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
        categoriesToProcess.offer(initialCategory);

        while (!categoriesToProcess.isEmpty()) {
            Category currentCategory = categoriesToProcess.poll();
            collectedCategoryIds.add(currentCategory.getId());

            List<Category> children = childrenMap.getOrDefault(currentCategory.getId(), Collections.emptyList());

            for (Category child : children) {
                categoriesToProcess.offer(child);
            }
        }
        return collectedCategoryIds;
    }

    private ProductResponse mapProductToResponseWithRating(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setTotalQuantity(product.getTotalQuantity());
        response.setTotalSold(product.getTotalSold());

        Double averageRating = reviewRepository.findAverageRatingByProductId(product.getId());
        double avg = (averageRating != null) ? averageRating : 0.0;
        avg = Math.round(avg * 10.0) / 10.0;

        response.setAverageRating(avg);
        return response;
    }

    @Override
    public String validateSortByField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field provided: '{}'. Defaulting to 'createdAt'.", sortBy);
            return "createdAt";
        }
        return sortBy;
    }

    @Transactional
    @Override
    @CacheEvict(value = "productDetails", key = "#productId")
    public ProductResponse updateProductById(Long productId, UpdateProductRequest request, Principal principal) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setDiscountPercent(request.getDiscountPercent());
        if(request.getImages() != null){
            product.setImages(request.getImages());
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.getVariants() != null) {
            Map<Long, ProductVariant> existingVariantsMap = product.getVariants().stream()
                    .collect(Collectors.toMap(ProductVariant::getId, v -> v, (v1, v2) -> v1));

            Set<ProductVariant> updatedVariants = new HashSet<>();

            for (ProductVariant variantRequest : request.getVariants()) {
                ProductVariant variantToUpdate;
                if (variantRequest.getId() != null && existingVariantsMap.containsKey(variantRequest.getId())) {
                    variantToUpdate = existingVariantsMap.get(variantRequest.getId());
                    existingVariantsMap.remove(variantRequest.getId());
                } else {
                    variantToUpdate = new ProductVariant();
                    variantToUpdate.setProduct(product);
                    variantToUpdate.setSold(0);
                }

                variantToUpdate.setColor(variantRequest.getColor());
                variantToUpdate.setSize(variantRequest.getSize());
                variantToUpdate.setQuantity(variantRequest.getQuantity());

                String sku = (product.getTitle() + "-" + variantRequest.getColor() + "-" + variantRequest.getSize())
                        .replaceAll("\\s+", "-").toUpperCase();
                variantToUpdate.setSku(sku);

                updatedVariants.add(variantToUpdate);
            }

            product.getVariants().clear();
            product.getVariants().addAll(updatedVariants);
        }

        Product updatedProduct = productRepository.save(product);
        return mapProductToResponse(updatedProduct);
    }

    @Transactional
    @Override
    @CacheEvict(value = "productDetails", key = "#productId")
    public String deleteProductById(Long productId, Principal principal) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        String username = principal.getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        boolean isAdmin = currentUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                    .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

            if (!product.getSeller().getId().equals(seller.getId())) {
                log.warn("User {} (Seller ID: {}) attempted to delete product {} owned by Seller ID: {}",
                        username, seller.getId(), productId, product.getSeller().getId());
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            log.info("Seller {} is deleting their own product (ID: {})", username, productId);
        } else {
            log.info("Admin {} is deleting product (ID: {}) owned by Seller ID: {}",
                    username, productId, product.getSeller().getId());
        }

        productRepository.delete(product);
        return "Product with ID " + productId + " has been deleted successfully.";
    }

    @Override
    public Integer calculateDiscountPercentage(BigDecimal price, BigDecimal sellingPrice) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) < 0) {
            return 0;
        }
        BigDecimal discount = price.subtract(sellingPrice);
        BigDecimal discountPercentage = discount.multiply(new BigDecimal("100")).divide(price, 0, RoundingMode.HALF_UP);
        return discountPercentage.intValue();
    }

    /**
     * Cập nhật: Thêm @CacheEvict để xóa cache productDetails khi update kho.
     * Sử dụng key="#result.id" để lấy ID của Product từ ProductResponse trả về.
     */
    @Transactional
    @Override
    @CacheEvict(value = "productDetails", key = "#result.id")
    public ProductResponse updateProductStock(Long variantId, UpdateStockRequest request, Principal principal) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", "id", variantId));
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));
        Product product = variant.getProduct();
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // Update logic
        variant.setQuantity(request.getQuantity());
        productVariantRepository.save(variant);

        // Fetch fresh product data for response
        Product updatedProduct = productRepository.findById(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", product.getId()));

        return mapProductToResponseWithRating(updatedProduct);
    }

    @Override
    public PageCustomResponse<ProductResponse> findProductBySeller(Long sellerId, Pageable pageable) {
        Page<Product> productPage = productRepository.findBySellerId(sellerId, pageable);
        List<ProductResponse> productResponses = productPage.getContent().stream()
                .map(this::mapProductToResponseWithRating)
                .collect(Collectors.toList());

        return PageCustomResponse.<ProductResponse>builder()
                .pageNo(productPage.getNumber() + 1)
                .pageSize(productPage.getSize())
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .pageContent(productResponses)
                .build();
    }

    @Override
    public PageCustomResponse<ProductResponse> getMyProducts(Principal principal, Pageable pageable) {
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

        return findProductBySeller(seller.getId(), pageable);
    }

    @Override
    public PageCustomResponse<ProductResponse> searchProduct(String keyword, Pageable pageable) {
        log.info("Searching products in Database with keyword: {}", keyword);
        Page<Product> productPage = productRepository.searchProducts(keyword, pageable);

        List<ProductResponse> productResponses = productPage.getContent().stream()
                .map(this::mapProductToResponseWithRating)
                .collect(Collectors.toList());

        return PageCustomResponse.<ProductResponse>builder()
                .pageNo(productPage.getNumber() + 1)
                .pageSize(productPage.getSize())
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .pageContent(productResponses)
                .build();
    }

    @Override
    public PageCustomResponse<ProductResponse> findProductByCategory(Long categoryId, Pageable pageable) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        Page<Product> productPage = productRepository.findByCategoryId(categoryId, pageable);

        List<ProductResponse> productResponses = productPage.getContent().stream()
                .map(this::mapProductToResponseWithRating)
                .collect(Collectors.toList());

        return PageCustomResponse.<ProductResponse>builder()
                .pageNo(productPage.getNumber() + 1)
                .pageSize(productPage.getSize())
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .pageContent(productResponses)
                .build();
    }
}