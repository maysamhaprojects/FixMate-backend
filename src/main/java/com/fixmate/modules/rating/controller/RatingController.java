package com.fixmate.modules.rating.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.rating.dto.RatingRequest;
import com.fixmate.modules.rating.model.Rating;
import com.fixmate.modules.rating.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    // Client: rate a pro after completed booking
    @PostMapping("/client/ratings")
    public ResponseEntity<Rating> rateBooking(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(ratingService.rateBooking(user, request));
    }

    // Client: which of my bookings I already rated (booking ids)
    @GetMapping("/client/rated-bookings")
    public ResponseEntity<List<Long>> getMyRatedBookings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ratingService.getClientRatedBookingIds(user.getId()));
    }

    // Public: get ratings for a pro
    @GetMapping("/pros/{proId}/ratings")
    public ResponseEntity<List<Rating>> getProRatings(@PathVariable Long proId) {
        return ResponseEntity.ok(ratingService.getProRatings(proId));
    }
}
