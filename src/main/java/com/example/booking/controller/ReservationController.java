package com.example.booking.controller;

import com.example.booking.dto.*;
import com.example.booking.entity.ReservationStatus;
import com.example.booking.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody ReservationCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(request, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<Page<ReservationResponse>> find(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return ResponseEntity.ok(
                reservationService.find(status, minPrice, maxPrice, page, size, sort,
                        authentication.getName(), admin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> findById(
            @PathVariable Long id,
            Authentication authentication) {

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return ResponseEntity.ok(
                reservationService.findById(id, authentication.getName(), admin));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ReservationUpdateRequest request,
            Authentication authentication) {

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return ResponseEntity.ok(
                reservationService.update(id, request, authentication.getName(), admin));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!admin) {
            throw new com.example.booking.exception.ForbiddenException(
                    "Only ADMIN can delete reservations");
        }

        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
