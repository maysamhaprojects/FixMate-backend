package com.fixmate.modules.pro.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.modules.pro.dto.ProProfileRequest;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.service.ProService;
import com.fixmate.modules.rating.model.Rating;
import com.fixmate.modules.rating.repository.RatingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pro")
@PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
public class ProController {

    private final ProService proService;
    private final BookingRepository bookingRepository;
    private final RatingRepository ratingRepository;

    public ProController(ProService proService,
                         BookingRepository bookingRepository,
                         RatingRepository ratingRepository) {
        this.proService = proService;
        this.bookingRepository = bookingRepository;
        this.ratingRepository = ratingRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<ProProfile> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(proService.getOrCreateProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProProfile> updateProfile(@AuthenticationPrincipal User user,
                                                     @RequestBody ProProfileRequest request) {
        return ResponseEntity.ok(proService.updateProfile(user, request));
    }

    /* סטטיסטיקות אמיתיות של בעל המקצוע המחובר */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal User user) {
        List<Booking> bookings = bookingRepository.findByProIdOrderByCreatedAtDesc(user.getId());

        // הזמנות חדשות = ממתינות (PENDING)
        long newOrders = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .count();

        // הזמנות היום = מתוזמנות להיום, ללא מבוטלות
        LocalDate today = LocalDate.now();
        long todayOrders = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> b.getScheduledAt() != null && b.getScheduledAt().toLocalDate().equals(today))
                .count();

        // הכנסה שבועית = סכום הזמנות שהושלמו ב-7 הימים האחרונים
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        double weeklyIncome = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(weekAgo))
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();

        ProProfile profile = proService.getOrCreateProfile(user);

        Map<String, Object> stats = new HashMap<>();
        stats.put("newOrders", newOrders);
        stats.put("todayOrders", todayOrders);
        stats.put("weeklyIncome", weeklyIncome);
        stats.put("rating", profile.getAverageRating() != null ? profile.getAverageRating() : 0.0);
        stats.put("totalRatings", profile.getTotalRatings() != null ? profile.getTotalRatings() : 0);
        return ResponseEntity.ok(stats);
    }

    /* הביקורות של בעל המקצוע המחובר (מבנה נקי, בלי חשיפת נתונים מיותרים) */
    @GetMapping("/reviews")
    public ResponseEntity<?> getMyReviews(@AuthenticationPrincipal User user) {
        List<Rating> ratings = ratingRepository.findByProId(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Rating r : ratings) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("clientName", r.getClient() != null ? r.getClient().getFullName() : "");
            m.put("score", r.getScore());
            m.put("comment", r.getComment());
            m.put("serviceType", r.getBooking() != null ? r.getBooking().getServiceType() : "");
            m.put("date", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            m.put("bookingId", r.getBooking() != null ? r.getBooking().getId() : null);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /* התראות — נגזרות מהזמנות חדשות ומדירוגים (בלי טבלה נפרדת) */
    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> notifs = new ArrayList<>();

        for (Booking b : bookingRepository.findByProIdOrderByCreatedAtDesc(user.getId())) {
            String clientName = b.getClient() != null ? b.getClient().getFullName() : "";
            if (b.getStatus() == BookingStatus.PENDING) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", "order-" + b.getId());
                m.put("type", "NEW_ORDER");
                m.put("clientName", clientName);
                m.put("detail", b.getServiceType());
                m.put("date", b.getCreatedAt() != null ? b.getCreatedAt().toString() : "");
                notifs.add(m);
            } else if (b.getStatus() == BookingStatus.CANCELLED) {
                // התראה על ביטול — כולל סיבת הביטול
                Map<String, Object> m = new HashMap<>();
                m.put("id", "cancel-" + b.getId());
                m.put("type", "CANCELLED");
                m.put("clientName", clientName);
                m.put("detail", b.getCancellationReason() != null && !b.getCancellationReason().isBlank()
                        ? b.getCancellationReason() : b.getServiceType());
                m.put("date", b.getCreatedAt() != null ? b.getCreatedAt().toString() : "");
                notifs.add(m);
            }
        }

        for (Rating r : ratingRepository.findByProId(user.getId())) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", "rating-" + r.getId());
            m.put("type", "RATING");
            m.put("clientName", r.getClient() != null ? r.getClient().getFullName() : "");
            m.put("detail", String.valueOf(r.getScore()));
            m.put("date", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            notifs.add(m);
        }

        // מיון מהחדש לישן
        notifs.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));
        return ResponseEntity.ok(notifs);
    }

    /* לוח הזמנים של היום — הזמנות שמתוזמנות להיום */
    @GetMapping("/schedule/today")
    public ResponseEntity<?> getTodaySchedule(@AuthenticationPrincipal User user) {
        List<Booking> bookings = bookingRepository.findByProIdOrderByCreatedAtDesc(user.getId());
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking b : bookings) {
            if (b.getScheduledAt() == null || !b.getScheduledAt().toLocalDate().equals(today)) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("clientName", b.getClient() != null ? b.getClient().getFullName() : "");
            m.put("clientPhone", b.getClient() != null ? b.getClient().getPhone() : "");
            m.put("serviceType", b.getServiceType());
            m.put("address", b.getAddress());
            m.put("status", b.getStatus() != null ? b.getStatus().name() : "");
            m.put("scheduledAt", b.getScheduledAt().toString());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }
}
