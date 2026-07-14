package com.fixmate.modules.booking.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.booking.dto.BookingRequest;
import com.fixmate.modules.booking.dto.EditBookingRequest;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import com.fixmate.modules.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // Client: create a booking
    @PostMapping("/client/bookings")
    public ResponseEntity<Booking> createBooking(@AuthenticationPrincipal User user,
                                                  @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.createBooking(user, request));
    }

    // Client: list my bookings
    @GetMapping("/client/bookings")
    public ResponseEntity<List<Booking>> getClientBookings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getClientBookings(user.getId()));
    }

    // Client: edit a pending booking (schedule / address / notes)
    @PutMapping("/client/bookings/{id}")
    public ResponseEntity<Booking> editBooking(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @RequestBody EditBookingRequest req) {
        return ResponseEntity.ok(
                bookingService.editBooking(id, user, req.getScheduledAt(), req.getAddress(), req.getNotes()));
    }

    // Client: cancel a booking
    @DeleteMapping("/client/bookings/{id}")
    public ResponseEntity<Void> cancelBooking(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @RequestParam(required = false) String reason) {
        bookingService.cancelBooking(id, user, reason);
        return ResponseEntity.noContent().build();
    }

    // Pro: list my orders
    @GetMapping("/pro/orders")
    public ResponseEntity<List<Booking>> getProOrders(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getProBookings(user.getId()));
    }

    // Pro: update order status
    @PutMapping("/pro/orders/{id}/status")
    public ResponseEntity<Booking> updateOrderStatus(@AuthenticationPrincipal User user,
                                                      @PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        BookingStatus status = BookingStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(bookingService.updateStatus(id, status, user));
    }
}
