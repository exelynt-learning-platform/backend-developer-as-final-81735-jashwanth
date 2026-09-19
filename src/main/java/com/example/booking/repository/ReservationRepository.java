package com.example.booking.repository;

import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
    boolean existsByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
            Long resourceId,
            LocalDateTime endTime,
            LocalDateTime startTime,
            ReservationStatus status
    );

    boolean existsByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNotAndIdNot(
            Long resourceId,
            LocalDateTime endTime,
            LocalDateTime startTime,
            ReservationStatus status,
            Long id
    );
}
