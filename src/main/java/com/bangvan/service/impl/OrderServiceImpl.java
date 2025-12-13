package com.bangvan.service.impl;

import com.bangvan.dto.request.order.CreateOrderRequest;
import com.bangvan.dto.response.PageCustomResponse;
import com.bangvan.dto.response.order.OrderItemResponse;
import com.bangvan.dto.response.order.OrderResponse;
import com.bangvan.dto.ws.SocketMessage;
import com.bangvan.entity.*;
import com.bangvan.exception.AppException;
import com.bangvan.exception.ErrorCode;
import com.bangvan.exception.ResourceNotFoundException;
import com.bangvan.repository.*;
import com.bangvan.service.NotificationService;
import com.bangvan.service.OrderService;
import com.bangvan.utils.OrderStatus;
import com.bangvan.utils.PaymentMethod;
import com.bangvan.utils.SocketEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final ModelMapper modelMapper;
    private final SellerRepository sellerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // Helper method để map entity sang response
    private OrderResponse mapOrderToOrderResponse(Order order) {
        OrderResponse orderResponse = modelMapper.map(order, OrderResponse.class);
        List<OrderItemResponse> orderItemResponses = order.getOrderItems().stream()
                .map(orderItem -> {
                    OrderItemResponse orderItemResponse = modelMapper.map(orderItem, OrderItemResponse.class);
                    orderItemResponse.setProduct(orderItem.getVariant().getProduct());
                    return orderItemResponse;
                })
                .collect(Collectors.toList());
        orderResponse.setOrderItems(orderItemResponses);
        return orderResponse;
    }

    /**
     * Gửi WebSocket message đến user cụ thể (Buyer/Seller)
     * Destination: /user/{username}/queue/updates
     */
    private void sendRealtimeUpdateToUser(String username, SocketEventType eventType, OrderResponse payload) {
        try {
            SocketMessage<OrderResponse> message = SocketMessage.of(eventType, payload);
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/updates",
                    message
            );
            log.info("WebSocket sent [{}] to user: {}", eventType, username);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Gửi WebSocket message đến Admin (Broadcast Topic)
     * Destination: /topic/admin/orders
     * Client Admin cần subscribe vào: /topic/admin/orders
     */
    private void sendRealtimeUpdateToAdmin(SocketEventType eventType, OrderResponse payload) {
        try {
            SocketMessage<OrderResponse> message = SocketMessage.of(eventType, payload);
            messagingTemplate.convertAndSend(
                    "/topic/admin/orders",
                    message
            );
            log.info("WebSocket broadcast [{}] to ADMIN topic", eventType);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to Admin topic: {}", e.getMessage());
        }
    }

    @Override
    public OrderResponse findOrderByOrderIdString(String orderId, Principal principal) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "OrderIdString", orderId));

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", principal.getName()));

        boolean isAdmin = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        boolean isBuyer = order.getUser().getId().equals(user.getId());

        boolean isSeller = false;
        Optional<Seller> sellerOpt = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(principal.getName());
        if (sellerOpt.isPresent()) {
            isSeller = order.getSeller().getId().equals(sellerOpt.get().getId());
        }

        if (!isAdmin && !isBuyer && !isSeller) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "You do not have permission to view this order.");
        }

        return mapOrderToOrderResponse(order);
    }

    @Override
    public PageCustomResponse<OrderResponse> findAllOrders(Pageable pageable) {
        Page<Order> orderPage = orderRepository.findAll(pageable);
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToOrderResponse)
                .collect(Collectors.toList());

        return PageCustomResponse.<OrderResponse>builder()
                .pageNo(orderPage.getNumber() + 1)
                .pageSize(orderPage.getSize())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .pageContent(orderResponses)
                .build();
    }

    @Transactional
    @Override
    public List<OrderResponse> createOrder(CreateOrderRequest request, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "user", username));

        if (cart.getCartItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        Address shippingAddress;
        if (request.getShippingAddress() != null) {
            Address newAddress = request.getShippingAddress();
            newAddress.setUser(user);
            shippingAddress = addressRepository.save(newAddress);
        } else if (request.getAddressId() != null) {
            shippingAddress = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Address", "ID", request.getAddressId()));
        } else {
            shippingAddress = user.getAddresses().stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        }

        Map<Seller, List<CartItem>> itemsBySeller = cart.getCartItems().stream()
                .collect(Collectors.groupingBy(cartItem -> cartItem.getVariant().getProduct().getSeller()));

        List<Order> newOrders = new ArrayList<>();

        for (Map.Entry<Seller, List<CartItem>> entry : itemsBySeller.entrySet()) {
            Seller seller = entry.getKey();

            if (seller.getId().equals(user.getId())) {
                log.warn("User (Seller ID: {}) attempted to order their own product.", user.getId());
                throw new AppException(ErrorCode.ACCESS_DENIED, "Sellers cannot purchase their own products. Product from seller ID " + seller.getId() + " is in your cart.");
            }

            List<CartItem> sellerCartItems = entry.getValue();

            Order order = new Order();
            order.setUser(user);
            order.setSeller(seller);
            order.setShippingAddress(shippingAddress);
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderDate(LocalDateTime.now());
            order.setOrderStatus(OrderStatus.PENDING);

            List<OrderItem> orderItems = new ArrayList<>();
            BigDecimal totalPriceForSeller = BigDecimal.ZERO;
            int totalItemForSeller = 0;

            // Xử lý kho hàng và lock sản phẩm
            for (CartItem cartItem : sellerCartItems) {
                ProductVariant variant = cartItem.getVariant();
                int requestedQuantity = cartItem.getQuantity();

                // Logic check stock cơ bản
                if (variant.getQuantity() < requestedQuantity) {
                    throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK,
                            "Not enough stock for SKU " + variant.getSku() + ". Only " + variant.getQuantity() + " left.");
                }

                variant.setQuantity(variant.getQuantity() - requestedQuantity);
                int currentSold = (variant.getSold() != null) ? variant.getSold() : 0;
                variant.setSold(currentSold + requestedQuantity);
                productVariantRepository.save(variant);

                OrderItem orderItem = new OrderItem();
                orderItem.setVariant(variant);
                orderItem.setQuantity(requestedQuantity);
                orderItem.setPriceAtPurchase(cartItem.getPrice());
                orderItem.setSellingPriceAtPurchase(cartItem.getSellingPrice());
                orderItem.setOrder(order);
                orderItem.setVariantSku(variant.getSku());
                orderItem.setColor(variant.getColor());
                orderItem.setSize(variant.getSize());
                orderItem.setProductTitle(variant.getProduct().getTitle());
                orderItems.add(orderItem);

                totalPriceForSeller = totalPriceForSeller.add(cartItem.getSellingPrice());
                totalItemForSeller += requestedQuantity;
            }

            order.setTotalPrice(totalPriceForSeller);
            order.setTotalItem(totalItemForSeller);
            order.setOrderItems(orderItems);

            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setAmount(totalPriceForSeller);
            paymentOrder.setPaymentMethod(PaymentMethod.VNPAY);
            paymentOrder.setUser(user);
            paymentOrder = paymentOrderRepository.save(paymentOrder);

            order.setPaymentOrder(paymentOrder);
            Order savedOrder = orderRepository.save(order);
            newOrders.add(savedOrder);

            // 1. Gửi Notification (DB) cho Seller & Admin
            String sellerMsg = "Bạn có đơn hàng mới #" + savedOrder.getOrderId() + " từ " + user.getUsername();
            notificationService.sendNotificationToSeller(seller, sellerMsg, "/seller/orders");

            String adminMsg = "Hệ thống có đơn hàng mới #" + savedOrder.getOrderId();
            notificationService.sendNotificationToAdmin(adminMsg, "/admin/orders");

            OrderResponse responsePayload = mapOrderToOrderResponse(savedOrder);

            // 2. Gửi WebSocket: Seller (Kênh riêng)
            sendRealtimeUpdateToUser(
                    seller.getUser().getUsername(),
                    SocketEventType.SELLER_NEW_ORDER,
                    responsePayload
            );

            // 3. Gửi WebSocket: Admin (Kênh chung - Realtime Dashboard)
            sendRealtimeUpdateToAdmin(
                    SocketEventType.ADMIN_NEW_ORDER,
                    responsePayload
            );
        }

        // Xóa cart sau khi đặt hàng thành công
        cart.getCartItems().clear();
        cart.setTotalItem(0);
        cart.setTotalSellingPrice(null);
        cartRepository.save(cart);

        return newOrders.stream()
                .map(this::mapOrderToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    public OrderResponse findOrderById(Long orderId, Principal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "ID", orderId));

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", principal.getName()));

        boolean isAdmin = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        boolean isBuyer = order.getUser().getId().equals(user.getId());

        boolean isSeller = false;
        Optional<Seller> sellerOpt = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(principal.getName());
        if (sellerOpt.isPresent()) {
            isSeller = order.getSeller().getId().equals(sellerOpt.get().getId());
        }

        if (!isAdmin && !isBuyer && !isSeller) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "You do not have permission to view this order.");
        }

        return mapOrderToOrderResponse(order);
    }

    @Override
    public PageCustomResponse<OrderResponse> findOrderByUser(Principal principal, Pageable pageable) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        Page<Order> orderPage = orderRepository.findByUser(user, pageable);

        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToOrderResponse)
                .collect(Collectors.toList());

        return PageCustomResponse.<OrderResponse>builder()
                .pageNo(orderPage.getNumber() + 1)
                .pageSize(orderPage.getSize())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .pageContent(orderResponses)
                .build();
    }

    @Override
    public PageCustomResponse<OrderResponse> findUserOrderHistory(Principal principal, Pageable pageable) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Page<Order> orderPage = orderRepository.findByUserAndOrderStatus(user, OrderStatus.DELIVERED, pageable);
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToOrderResponse)
                .collect(Collectors.toList());
        return PageCustomResponse.<OrderResponse>builder()
                .pageNo(orderPage.getNumber() + 1)
                .pageSize(orderPage.getSize())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .pageContent(orderResponses)
                .build();
    }

    @Override
    public PageCustomResponse<OrderResponse> getSellerOrders(Principal principal, Pageable pageable) {
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

        Page<Order> orderPage = orderRepository.findBySeller(seller, pageable);

        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapOrderToOrderResponse)
                .collect(Collectors.toList());

        return PageCustomResponse.<OrderResponse>builder()
                .pageNo(orderPage.getNumber() + 1)
                .pageSize(orderPage.getSize())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .pageContent(orderResponses)
                .build();
    }

    @Transactional
    @Override
    public OrderResponse updateOrderStatus(String orderId, String status, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "ID", orderId));

        boolean isAdmin = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        // Kiểm tra quyền: Chỉ Admin hoặc Seller sở hữu đơn mới được update
        if (!isAdmin) {
            Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                    .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

            if (!order.getSeller().getId().equals(seller.getId())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        order.setOrderStatus(newStatus);

        Order updatedOrder = orderRepository.save(order);
        OrderResponse response = mapOrderToOrderResponse(updatedOrder);

        // 1. Notification Database
        String msg = "Đơn hàng #" + order.getOrderId() + " của bạn đã chuyển sang trạng thái: " + newStatus.name();
        notificationService.sendNotificationToUser(order.getUser(), msg, "/profile/orders/" + order.getOrderId());

        // 2. Realtime WebSocket cho Buyer (Quan trọng: Buyer cần biết Admin/Seller đã update)
        sendRealtimeUpdateToUser(
                order.getUser().getUsername(),
                SocketEventType.BUYER_ORDER_UPDATE,
                response
        );

        // 3. Nếu người thực hiện là Admin, có thể cũng cần update lại cho Seller biết
        if (isAdmin) {
            sendRealtimeUpdateToUser(
                    order.getSeller().getUser().getUsername(),
                    SocketEventType.SELLER_NEW_ORDER, // Hoặc tạo type SELLER_ORDER_UPDATE riêng nếu cần
                    response
            );
        }

        // 4. Update cho Admin dashboard (Sync giữa các admin)
        sendRealtimeUpdateToAdmin(SocketEventType.ADMIN_ORDER_UPDATE, response);

        return response;
    }

    @Transactional
    @Override
    public String deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "ID", orderId));
        orderRepository.delete(order);
        return "Order with ID " + orderId + " has been deleted successfully.";
    }

    @Transactional
    @Override
    public OrderResponse cancelOrder(String orderId, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "OrderIdString", orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Set<OrderStatus> cancellableStatuses = Set.of(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED
        );

        if (!cancellableStatuses.contains(order.getOrderStatus())) {
            log.warn("User {} attempted to cancel order {} with status {}. Cancellation not allowed.",
                    username, orderId, order.getOrderStatus());
            throw new AppException(ErrorCode.ORDER_CANCELLATION_NOT_ALLOWED);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        // Hoàn trả tồn kho (Race condition handling: database row lock on productVariant suggested for high load)
        for (OrderItem item : order.getOrderItems()) {
            ProductVariant variant = item.getVariant();

            variant.setQuantity(variant.getQuantity() + item.getQuantity());
            int currentSold = (variant.getSold() != null) ? variant.getSold() : 0;
            variant.setSold(Math.max(0, currentSold - item.getQuantity()));
            productVariantRepository.save(variant);
        }

        Order cancelledOrder = orderRepository.save(order);
        OrderResponse response = mapOrderToOrderResponse(cancelledOrder);

        // 1. Notification Database
        String msg = "Người mua đã hủy đơn hàng #" + order.getOrderId();
        notificationService.sendNotificationToSeller(order.getSeller(), msg, "/seller/orders");
        notificationService.sendNotificationToAdmin("Đơn hàng #" + order.getOrderId() + " đã bị hủy bởi người mua.", "/admin/orders");

        // 2. Realtime WebSocket cho Seller
        sendRealtimeUpdateToUser(
                order.getSeller().getUser().getUsername(),
                SocketEventType.SELLER_ORDER_CANCELLED,
                response
        );

        // 3. Realtime WebSocket cho Admin (Realtime Dashboard)
        sendRealtimeUpdateToAdmin(
                SocketEventType.ADMIN_ORDER_CANCELLED,
                response
        );

        return response;
    }
}