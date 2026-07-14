package com.fixmate.modules.booking.repository;

import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<Booking> findByProIdOrderByCreatedAtDesc(Long proId);

    List<Booking> findByProIdAndStatusOrderByScheduledAtAsc(Long proId, BookingStatus status);

    long countByStatus(BookingStatus status);
}
