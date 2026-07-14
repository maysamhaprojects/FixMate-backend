package com.fixmate.modules.admin.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.repository.ProProfileRepository;
import com.fixmate.modules.complaint.model.ComplaintStatus;
import com.fixmate.modules.complaint.repository.ComplaintRepository;
import com.fixmate.modules.rating.model.Rating;
import com.fixmate.modules.rating.repository.RatingRepository;
import com.fixmate.common.email.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProProfileRepository proProfileRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ComplaintRepository complaintRepository;
    private final RatingRepository ratingRepository;
    private final EmailService emailService;

    public AdminController(ProProfileRepository proProfileRepository,
                           UserRepository userRepository,
                           BookingRepository bookingRepository,
                           ComplaintRepository complaintRepository,
                           RatingRepository ratingRepository,
                           EmailService emailService) {
        this.proProfileRepository = proProfileRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.complaintRepository = complaintRepository;
        this.ratingRepository = ratingRepository;
        this.emailService = emailService;
    }

    @GetMapping("/pros/pending")
    public ResponseEntity<List<ProProfile>> getPendingPros() {
        // רק ממתינים אמיתיים — לא מאושרים וגם לא נדחים
        List<ProProfile> pending = proProfileRepository.findByApprovedFalse().stream()
                .filter(p -> !p.isRejected())
                .toList();
        return ResponseEntity.ok(pending);
    }

    @PutMapping("/pros/{id}/approve")
    public ResponseEntity<?> approvePro(@PathVariable Long id) {
        return proProfileRepository.findById(id).map(pro -> {
            pro.setApproved(true);
            proProfileRepository.save(pro);
            User u = pro.getUser();
            if (u != null) emailService.send(u.getEmail(),
                "FixMate — החשבון שלך אושר! 🎉",
                "שלום " + u.getFullName() + ",\n\n" +
                "בקשתך להצטרף כבעל מקצוע ב-FixMate אושרה!\n" +
                "אפשר להתחבר עכשיו ולהתחיל לקבל הזמנות.\n\n" +
                "בהצלחה,\nצוות FixMate");
            return ResponseEntity.ok("Approved");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/pros/{id}/reject")
    public ResponseEntity<?> rejectPro(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        // דחייה = סימון כנדחה + סיבה (לא מוחקים, כדי שבעל המקצוע יקבל הסבר)
        return proProfileRepository.findById(id).map(profile -> {
            profile.setRejected(true);
            profile.setApproved(false);
            if (reason != null && !reason.isBlank()) profile.setRejectionReason(reason.trim());
            proProfileRepository.save(profile);
            User u = profile.getUser();
            if (u != null) emailService.send(u.getEmail(),
                "FixMate — עדכון לגבי בקשתך",
                "שלום " + u.getFullName() + ",\n\n" +
                "לצערנו בקשתך להצטרף כבעל מקצוע ב-FixMate נדחתה.\n" +
                (reason != null && !reason.isBlank() ? ("סיבה: " + reason.trim() + "\n") : "") +
                "\nאפשר לפנות לתמיכה או להירשם מחדש לאחר תיקון הפרטים.\n\n" +
                "צוות FixMate");
            return ResponseEntity.ok("Rejected");
        }).orElse(ResponseEntity.notFound().build());
    }

    /* סטטיסטיקות אמיתיות לדשבורד */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        List<User> users = userRepository.findAll();
        long clients = users.stream().filter(u -> u.getRole() != null && u.getRole().name().equals("CLIENT")).count();
        long pros = users.stream().filter(u -> u.getRole() != null && u.getRole().name().equals("PROFESSIONAL")).count();

        List<Booking> bookings = bookingRepository.findAll();
        double revenue = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", users.size());
        stats.put("totalClients", clients);
        stats.put("totalPros", pros);
        stats.put("totalOrders", bookings.size());
        stats.put("revenue", revenue);
        stats.put("pendingApprovals", proProfileRepository.findByApprovedFalse().size());
        long openComplaints = complaintRepository.findAll().stream()
                .filter(c -> c.getStatus() != ComplaintStatus.RESOLVED)
                .count();
        stats.put("openComplaints", openComplaints);
        return ResponseEntity.ok(stats);
    }

    /* רשימת כל המשתמשים */
    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("fullName", u.getFullName());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone());
            m.put("role", u.getRole() != null ? u.getRole().name() : null);
            m.put("suspended", u.isSuspended());
            m.put("joined", u.getCreatedAt() != null ? u.getCreatedAt().toString().substring(0, 10) : null);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /* השעיה / שחזור משתמש */
    @PutMapping("/users/{id}/toggle-suspend")
    public ResponseEntity<?> toggleSuspend(@PathVariable Long id) {
        return userRepository.findById(id).map(u -> {
            u.setSuspended(!u.isSuspended());
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("id", u.getId(), "suspended", u.isSuspended()));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* רשימת כל ההזמנות */
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking b : bookingRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("client", b.getClient() != null ? b.getClient().getFullName() : "");
            m.put("pro", b.getPro() != null ? b.getPro().getFullName() : "");
            m.put("serviceType", b.getServiceType());
            m.put("status", b.getStatus() != null ? b.getStatus().name() : "");
            m.put("price", b.getTotalPrice() != null ? b.getTotalPrice() : 0);
            m.put("date", b.getScheduledAt() != null ? b.getScheduledAt().toString() : "");
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /* כל הדירוגים בפלטפורמה (לפיקוח האדמין) */
    @GetMapping("/ratings")
    public ResponseEntity<?> getRatings() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Rating r : ratingRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("client", r.getClient() != null ? r.getClient().getFullName() : "");
            m.put("pro", r.getPro() != null ? r.getPro().getFullName() : "");
            m.put("score", r.getScore());
            m.put("comment", r.getComment());
            m.put("bookingId", r.getBooking() != null ? r.getBooking().getId() : null);
            m.put("service", r.getBooking() != null ? r.getBooking().getServiceType() : "");
            m.put("date", r.getCreatedAt() != null ? r.getCreatedAt().toString().substring(0, 10) : null);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        long pending = proProfileRepository.findByApprovedFalse().size();
        return ResponseEntity.ok(Map.of("pendingApprovals", pending));
    }
}
