package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long resourceId,
        String resourceName,
        Long userId,
        String username,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal price,
        String status
) {}
