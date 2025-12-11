package com.bangvan.dto.response.seller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryResponse {

    private long unreadCount;

    private long totalCount;
}