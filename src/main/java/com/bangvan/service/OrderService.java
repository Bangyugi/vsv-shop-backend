package com.bangvan.service;

import com.bangvan.dto.request.order.CreateOrderRequest;
import com.bangvan.dto.response.PageCustomResponse;
import com.bangvan.dto.response.order.OrderResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;

public interface OrderService {
    @Transactional
    List<OrderResponse>  createOrder(CreateOrderRequest request, Principal principal);

    PageCustomResponse<OrderResponse> findOrderByUser(Principal principal, Pageable pageable);

    PageCustomResponse<OrderResponse> findUserOrderHistory(Principal principal, Pageable pageable);

    PageCustomResponse<OrderResponse> getSellerOrders(Principal principal, Pageable pageable);

    OrderResponse findOrderById(Long orderId, Principal principal);

    OrderResponse findOrderByOrderIdString(String orderId, Principal principal);

    @Transactional
    OrderResponse updateOrderStatus(String orderId, String status, Principal principal);

    @Transactional
    String deleteOrder(Long orderId);

    PageCustomResponse<OrderResponse> findAllOrders(Pageable pageable);

    @Transactional
    OrderResponse cancelOrder(String orderId, Principal principal);
}
