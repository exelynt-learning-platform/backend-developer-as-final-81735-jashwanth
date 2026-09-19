package com.example.booking.service;

import com.example.booking.dto.*;
import com.example.booking.entity.*;
import com.example.booking.exception.*;
import com.example.booking.repository.*;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            ResourceRepository resourceRepository,
            UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
    }

    public ReservationResponse create(ReservationCreateRequest request, String username) {
        validateTime(request.startTime(), request.endTime());

        BookableResource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + request.resourceId()));

        if (!resource.getAvailable()) {
            throw new BadRequestException("Resource is not available");
        }

        if (reservationRepository.existsByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                resource.getId(), request.endTime(), request.startTime(), ReservationStatus.CANCELLED)) {
            throw new BadRequestException("Resource is already reserved for the selected time");
        }

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Reservation reservation = new Reservation();
        reservation.setResource(resource);
        reservation.setUser(user);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setPrice(calculatePrice(resource, request.startTime(), request.endTime()));
        reservation.setStatus(ReservationStatus.PENDING);

        return toResponse(reservationRepository.save(reservation));
    }

    public Page<ReservationResponse> find(
            ReservationStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            String sort,
            String username,
            boolean admin) {

        if (page < 0) throw new BadRequestException("Page must be zero or greater");
        if (size < 1 || size > 100) throw new BadRequestException("Size must be between 1 and 100");
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BadRequestException("Minimum price cannot be negative");
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BadRequestException("Maximum price cannot be negative");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new BadRequestException("Minimum price cannot exceed maximum price");

        Sort sorting = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sorting);
        Specification<Reservation> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        if (!admin) {
            spec = spec.and((root, query, cb) -> {
                Join<Reservation, AppUser> user = root.join("user");
                return cb.equal(user.get("username"), username);
            });
        }

        return reservationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public ReservationResponse findById(Long id, String username, boolean admin) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
        ensureOwnerOrAdmin(reservation, username, admin);
        return toResponse(reservation);
    }

    public ReservationResponse update(Long id, ReservationUpdateRequest request, String username, boolean admin) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));

        ensureOwnerOrAdmin(reservation, username, admin);
        validateTime(request.startTime(), request.endTime());

        BookableResource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + request.resourceId()));

        ReservationStatus status;
        try {
            status = ReservationStatus.valueOf(request.status().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid reservation status");
        }

        if (!resource.getAvailable() && status != ReservationStatus.CANCELLED) {
            throw new BadRequestException("Resource is not available");
        }

        if (status != ReservationStatus.CANCELLED &&
                reservationRepository.existsByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNotAndIdNot(
                        resource.getId(), request.endTime(), request.startTime(),
                        ReservationStatus.CANCELLED, reservation.getId())) {
            throw new BadRequestException("Resource is already reserved for the selected time");
        }

        reservation.setResource(resource);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setPrice(calculatePrice(resource, request.startTime(), request.endTime()));
        reservation.setStatus(status);

        return toResponse(reservationRepository.save(reservation));
    }

    public void delete(Long id) {
        if (!reservationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reservation not found: " + id);
        }
        reservationRepository.deleteById(id);
    }

    private void ensureOwnerOrAdmin(Reservation reservation, String username, boolean admin) {
        if (!admin && !reservation.getUser().getUsername().equals(username)) {
            throw new ForbiddenException("You can access only your own reservations");
        }
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new BadRequestException("Start time must be before end time");
        }
    }

    private BigDecimal calculatePrice(BookableResource resource, LocalDateTime start, LocalDateTime end) {
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (minutes <= 0) throw new BadRequestException("Reservation duration must be positive");
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        return resource.getPricePerHour().multiply(hours).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim() : "asc";

        if (!field.matches("id|price|startTime|endTime|status")) {
            throw new BadRequestException("Invalid sort field");
        }

        Sort.Direction dir;
        if (direction.equalsIgnoreCase("asc")) {
            dir = Sort.Direction.ASC;
        } else if (direction.equalsIgnoreCase("desc")) {
            dir = Sort.Direction.DESC;
        } else {
            throw new BadRequestException("Invalid sort direction");
        }

        return Sort.by(dir, field);
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getResource().getId(),
                r.getResource().getName(),
                r.getUser().getId(),
                r.getUser().getUsername(),
                r.getStartTime(),
                r.getEndTime(),
                r.getPrice(),
                r.getStatus().name()
        );
    }
}
