package com.bangvan.service;

import com.bangvan.dto.response.seller.NotificationSummaryResponse;
import com.bangvan.entity.Seller;
import com.bangvan.entity.User;

import java.security.Principal;

public interface NotificationService {
    NotificationSummaryResponse getNotificationSummary(Principal principal);

    void sendNotificationToSeller(Seller seller, String message, String link);

    void sendNotificationToUser(User user, String message, String link);

    void sendNotificationToAdmin(String message, String link);
}