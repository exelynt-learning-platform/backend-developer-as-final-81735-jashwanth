package com.example.booking.dto;

import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * Partial reservation update request.
 * Fields may be omitted; omitted values keep the current reservation value.
 * This also allows status-only cancellation of an in-progress reservation.
 */
public record ReservationUpdateRequest(
        Long resourceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @Pattern(
                regexp = "(?i)PENDING|CONFIRMED|CANCELLED",
                message = "Status must be PENDING, CONFIRMED or CANCELLED")
        String status
) {}
