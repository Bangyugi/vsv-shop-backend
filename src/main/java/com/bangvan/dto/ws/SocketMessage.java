package com.bangvan.dto.ws;

import com.bangvan.utils.SocketEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocketMessage<T> {
    private SocketEventType type;
    private T payload;
    private LocalDateTime timestamp;

    public static <T> SocketMessage<T> of(SocketEventType type, T payload) {
        return SocketMessage.<T>builder()
                .type(type)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();
    }
}