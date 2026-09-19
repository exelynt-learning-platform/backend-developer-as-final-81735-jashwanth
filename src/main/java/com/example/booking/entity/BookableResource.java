package com.example.booking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Entity
@Table(name = "resources")
public class BookableResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String description;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String type;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHour;

    @NotNull
    @Column(nullable = false)
    private Boolean available = true;

    public BookableResource() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public BigDecimal getPricePerHour() { return pricePerHour; }
    public Boolean getAvailable() { return available; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setType(String type) { this.type = type; }
    public void setPricePerHour(BigDecimal pricePerHour) { this.pricePerHour = pricePerHour; }
    public void setAvailable(Boolean available) { this.available = available; }
}
