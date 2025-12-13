package com.bangvan.utils;


public enum SocketEventType {
    SELLER_NEW_ORDER,
    SELLER_ORDER_CANCELLED,

    BUYER_ORDER_UPDATE,

    // Sự kiện cho Admin (MỚI)
    ADMIN_NEW_ORDER,        // Có đơn hàng mới toàn hệ thống
    ADMIN_ORDER_CANCELLED,  // Có đơn hàng bị hủy
    ADMIN_ORDER_UPDATE      // Cập nhật trạng thái khác
}