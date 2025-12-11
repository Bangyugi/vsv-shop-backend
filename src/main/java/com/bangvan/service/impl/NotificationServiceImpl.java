package com.bangvan.service.impl;

import com.bangvan.dto.response.notification.NotificationResponse;
import com.bangvan.dto.response.seller.NotificationSummaryResponse;
import com.bangvan.entity.Notification;
import com.bangvan.entity.Seller;
import com.bangvan.entity.User;
import com.bangvan.exception.ResourceNotFoundException;
import com.bangvan.repository.NotificationRepository;
import com.bangvan.repository.SellerRepository;
import com.bangvan.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SellerRepository sellerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(readOnly = true)
    public NotificationSummaryResponse getNotificationSummary(Principal principal) {
        String username = principal.getName();
        Seller seller = sellerRepository.findByUser_UsernameAndUser_EnabledIsTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller", "username", username));

        return notificationRepository.getSummaryBySeller(seller);
    }

    @Override
    @Transactional
    public void sendNotificationToSeller(Seller seller, String message, String link) {

        Notification notification = new Notification();
        notification.setSeller(seller);
        notification.setMessage(message);
        notification.setLink(link);
        notification.setRead(false);


        Notification savedNotification = notificationRepository.save(notification);



        NotificationResponse response = NotificationResponse.builder()
                .id(savedNotification.getId())
                .message(savedNotification.getMessage())
                .link(savedNotification.getLink())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        String username = seller.getUser().getUsername();


        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", response);
        log.info("Sent realtime notification to seller: {}", username);
    }

    @Override
    public void sendNotificationToUser(User user, String message, String link) {




        NotificationResponse response = NotificationResponse.builder()
                .id(null)
                .message(message)
                .link(link)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        String username = user.getUsername();
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", response);
        log.info("Sent realtime notification to user: {}", username);
    }

    @Override
    public void sendNotificationToAdmin(String message, String link) {



        NotificationResponse response = NotificationResponse.builder()
                .message(message)
                .link(link)
                .createdAt(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/admin/notifications", response);
        log.info("Sent realtime notification to admin topic");
    }
}