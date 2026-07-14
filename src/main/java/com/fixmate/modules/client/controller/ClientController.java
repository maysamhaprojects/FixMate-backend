package com.fixmate.modules.client.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.service.ProService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client")
public class ClientController {

    private final ProService proService;
    private final BookingRepository bookingRepository;

    public ClientController(ProService proService, BookingRepository bookingRepository) {
        this.proService = proService;
        this.bookingRepository = bookingRepository;
    }

    // Search pros by specialty or location
    @GetMapping("/pros")
    public ResponseEntity<List<ProProfile>> searchPros(
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(proService.searchPros(specialty, location));
    }

    // Get a single pro profile
    @GetMapping("/pros/{id}")
    public ResponseEntity<ProProfile> getProProfile(@PathVariable Long id) {
        return ResponseEntity.ok(proService.getById(id));
    }

    // התראות ללקוח — נגזרות מסטטוס ההזמנות שלו
    @GetMapping("/notifications")
    public ResponseEntity<?> getClientNotifications(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> notifs = new ArrayList<>();
        for (Booking b : bookingRepository.findByClientIdOrderByCreatedAtDesc(user.getId())) {
            String type;
            switch (b.getStatus()) {
                case CONFIRMED:   type = "confirmed"; break;
                case IN_PROGRESS: type = "in_progress"; break;
                case COMPLETED:   type = "completed"; break;
                case CANCELLED:   type = "cancelled"; break;
                default:          continue; // PENDING — אין התראה
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", type + "-" + b.getId());
            m.put("type", type);
            m.put("proName", b.getPro() != null ? b.getPro().getFullName() : "");
            m.put("serviceType", b.getServiceType());
            m.put("date", b.getScheduledAt() != null ? b.getScheduledAt().toString() : "");
            notifs.add(m);
        }
        return ResponseEntity.ok(notifs);
    }
}
