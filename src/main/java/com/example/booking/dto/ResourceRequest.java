package com.example.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ResourceRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Description is required") String description,
        @NotBlank(message = "Type is required") String type,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must be zero or greater") BigDecimal pricePerHour,
        @NotNull(message = "Availability is required") Boolean available
) {}
