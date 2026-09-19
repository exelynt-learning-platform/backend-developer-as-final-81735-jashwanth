package com.example.booking.service;

import com.example.booking.dto.ReservationCreateRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.ReservationUpdateRequest;
import com.example.booking.entity.AppUser;
import com.example.booking.entity.BookableResource;
import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import com.example.booking.exception.BadRequestException;
import com.example.booking.exception.ForbiddenException;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Transactional
public class ReservationService {

    private static final int MAX_PAGE_SIZE = 100;

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
        validateFutureTime(request.startTime(), request.endTime());

        BookableResource resource = findResource(request.resourceId());
        ensureResourceAvailable(resource);
        ensureNoOverlap(resource.getId(), request.startTime(), request.endTime(), null);

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

    @Transactional(readOnly = true)
    public Page<ReservationResponse> find(
            ReservationStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            String sort,
            String username,
            boolean admin) {

        validatePageAndPriceFilters(page, size, minPrice, maxPrice);

        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Specification<Reservation> spec = Specification.unrestricted();

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

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id, String username, boolean admin) {
        Reservation reservation = findReservation(id);
        ensureOwnerOrAdmin(reservation, username, admin);
        return toResponse(reservation);
    }

    public ReservationResponse update(
            Long id,
            ReservationUpdateRequest request,
            String username,
            boolean admin) {

        Reservation reservation = findReservation(id);
        ensureOwnerOrAdmin(reservation, username, admin);

        if (request.resourceId() == null
                && request.startTime() == null
                && request.endTime() == null
                && request.status() == null) {
            throw new BadRequestException("At least one reservation field must be provided");
        }

        ReservationStatus requestedStatus = request.status() == null
                ? reservation.getStatus()
                : parseStatus(request.status());

        // A cancellation may be status-only and must not be blocked by @Future
        // validation when the booking has already started.
        if (requestedStatus == ReservationStatus.CANCELLED
                && request.resourceId() == null
                && request.startTime() == null
                && request.endTime() == null) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            return toResponse(reservationRepository.save(reservation));
        }

        BookableResource resource = request.resourceId() == null
                ? reservation.getResource()
                : findResource(request.resourceId());
        LocalDateTime startTime = request.startTime() == null
                ? reservation.getStartTime()
                : request.startTime();
        LocalDateTime endTime = request.endTime() == null
                ? reservation.getEndTime()
                : request.endTime();

        if (requestedStatus != ReservationStatus.CANCELLED) {
            validateFutureTime(startTime, endTime);
            ensureResourceAvailable(resource);
            ensureNoOverlap(resource.getId(), startTime, endTime, reservation.getId());
        } else {
            validateTimeOrder(startTime, endTime);
        }

        reservation.setResource(resource);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setPrice(calculatePrice(resource, startTime, endTime));
        reservation.setStatus(requestedStatus);

        return toResponse(reservationRepository.save(reservation));
    }

    public void delete(Long id) {
        Reservation reservation = findReservation(id);
        reservationRepository.delete(reservation);
    }

    private Reservation findReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }

    private BookableResource findResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id));
    }

    private void ensureOwnerOrAdmin(Reservation reservation, String username, boolean admin) {
        if (!admin && !reservation.getUser().getUsername().equals(username)) {
            throw new ForbiddenException("You can access only your own reservations");
        }
    }

    private void ensureResourceAvailable(BookableResource resource) {
        if (!Boolean.TRUE.equals(resource.getAvailable())) {
            throw new BadRequestException("Resource is not available");
        }
    }

    private void ensureNoOverlap(
            Long resourceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludedReservationId) {

        boolean overlaps = excludedReservationId == null
                ? reservationRepository.existsByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                        resourceId, endTime, startTime, ReservationStatus.CANCELLED)
                : reservationRepository.existsByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNotAndIdNot(
                        resourceId, endTime, startTime, ReservationStatus.CANCELLED, excludedReservationId);

        if (overlaps) {
            throw new BadRequestException("Resource is already reserved for the selected time");
        }
    }

    private void validatePageAndPriceFilters(
            int page,
            int size,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Minimum price cannot be negative");
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Maximum price cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Minimum price cannot exceed maximum price");
        }
    }

    private void validateFutureTime(LocalDateTime start, LocalDateTime end) {
        validateTimeOrder(start, end);
        LocalDateTime now = LocalDateTime.now();
        if (!start.isAfter(now) || !end.isAfter(now)) {
            throw new BadRequestException("Reservation times must be in the future");
        }
    }

    private void validateTimeOrder(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BadRequestException("Start time and end time are required");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("Start time must be before end time");
        }
    }

    private ReservationStatus parseStatus(String status) {
        try {
            return ReservationStatus.valueOf(status.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid reservation status");
        }
    }

    private BigDecimal calculatePrice(BookableResource resource, LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            throw new BadRequestException("Reservation duration must be positive");
        }
        BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return resource.getPricePerHour()
                .multiply(hours)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }

        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new BadRequestException("Sort must use field,asc or field,desc");
        }

        String field = parts[0].trim();
        String direction = parts.length == 2 ? parts[1].trim() : "asc";

        if (!field.matches("id|price|startTime|endTime|status")) {
            throw new BadRequestException("Invalid sort field");
        }

        Sort.Direction sortDirection;
        if (direction.equalsIgnoreCase("asc")) {
            sortDirection = Sort.Direction.ASC;
        } else if (direction.equalsIgnoreCase("desc")) {
            sortDirection = Sort.Direction.DESC;
        } else {
            throw new BadRequestException("Invalid sort direction");
        }

        return Sort.by(sortDirection, field);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getResource().getId(),
                reservation.getResource().getName(),
                reservation.getUser().getId(),
                reservation.getUser().getUsername(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice(),
                reservation.getStatus().name());
    }
}
