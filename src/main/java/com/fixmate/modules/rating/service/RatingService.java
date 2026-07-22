package com.fixmate.modules.rating.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.repository.ProProfileRepository;
import com.fixmate.modules.rating.dto.RatingRequest;
import com.fixmate.modules.rating.model.Rating;
import com.fixmate.modules.rating.repository.RatingRepository;
import com.fixmate.common.email.EmailService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final BookingRepository bookingRepository;
    private final ProProfileRepository proProfileRepository;
    private final EmailService emailService;

    public RatingService(RatingRepository ratingRepository,
                         BookingRepository bookingRepository,
                         ProProfileRepository proProfileRepository,
                         EmailService emailService) {
        this.ratingRepository = ratingRepository;
        this.bookingRepository = bookingRepository;
        this.proProfileRepository = proProfileRepository;
        this.emailService = emailService;
    }

    public Rating rateBooking(User client, RatingRequest req) {
        Booking booking = bookingRepository.findById(req.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Unauthorized: not your booking");
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new RuntimeException("Can only rate completed bookings");
        }

        if (ratingRepository.existsByBookingId(booking.getId())) {
            throw new RuntimeException("Booking already rated");
        }

        Rating rating = new Rating();
        rating.setBooking(booking);
        rating.setClient(client);
        rating.setPro(booking.getPro());
        rating.setScore(req.getScore());
        rating.setComment(req.getComment());
        Rating saved = ratingRepository.save(rating);

        // Update pro average rating
        proProfileRepository.findByUserId(booking.getPro().getId()).ifPresent(profile -> {
            Double avg = ratingRepository.findAverageScoreByProId(booking.getPro().getId());
            long count = ratingRepository.findByProId(booking.getPro().getId()).size();
            profile.setAverageRating(avg != null ? avg : 0.0);
            profile.setTotalRatings((int) count);
            proProfileRepository.save(profile);
        });

        User pro = booking.getPro();
        String stars = "★".repeat(req.getScore()) + "☆".repeat(Math.max(0, 5 - req.getScore()));
        String commentLine = (req.getComment() != null && !req.getComment().isBlank())
                ? ("תגובה: " + req.getComment() + "\n") : "";

        // מייל לבעל המקצוע — קיבלת דירוג חדש
        if (pro != null) {
            emailService.send(pro.getEmail(),
                "FixMate — קיבלת דירוג חדש!",
                "שלום " + pro.getFullName() + ",\n\n" +
                client.getFullName() + " דירג/ה את השירות שלך:\n" +
                stars + " (" + req.getScore() + "/5)\n" +
                commentLine +
                "\nהדירוג מופיע עכשיו בפרופיל שלך ב-FixMate.\n\nצוות FixMate");
        }

        // אישור ללקוח — תיעוד הדירוג שנשלח
        emailService.send(client.getEmail(),
            "FixMate — הדירוג שלך נשמר",
            "שלום " + client.getFullName() + ",\n\n" +
            "תודה! הדירוג שלך" + (pro != null ? (" עבור " + pro.getFullName()) : "") + " נשמר:\n" +
            stars + " (" + req.getScore() + "/5)\n" +
            commentLine +
            "\nצוות FixMate");

        return saved;
    }

    public List<Rating> getProRatings(Long proId) {
        return ratingRepository.findByProId(proId);
    }

    /* מזהי ההזמנות שהלקוח כבר דירג — כדי שהממשק יסתיר את כפתור "דרג" */
    public List<Long> getClientRatedBookingIds(Long clientId) {
        return ratingRepository.findByClientId(clientId).stream()
                .filter(r -> r.getBooking() != null)
                .map(r -> r.getBooking().getId())
                .toList();
    }
}
