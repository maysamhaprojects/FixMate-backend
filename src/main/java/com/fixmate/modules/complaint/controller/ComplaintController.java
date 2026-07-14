package com.fixmate.modules.complaint.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.complaint.dto.ComplaintRequest;
import com.fixmate.modules.complaint.model.Complaint;
import com.fixmate.modules.complaint.model.ComplaintStatus;
import com.fixmate.modules.complaint.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    /* ── לקוח / בעל מקצוע: הגשת תלונה ── */
    @PostMapping("/complaints")
    public ResponseEntity<?> create(@AuthenticationPrincipal User user,
                                    @Valid @RequestBody ComplaintRequest req) {
        Complaint c = complaintService.create(user, req);
        return ResponseEntity.ok(toMap(c));
    }

    /* ── לקוח / בעל מקצוע: התלונות שלי ── */
    @GetMapping("/complaints/mine")
    public ResponseEntity<?> mine(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Complaint c : complaintService.getMine(user.getId())) result.add(toMap(c));
        return ResponseEntity.ok(result);
    }

    /* ── אדמין: כל התלונות ── */
    @GetMapping("/admin/complaints")
    public ResponseEntity<?> all() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Complaint c : complaintService.getAll()) result.add(toMap(c));
        return ResponseEntity.ok(result);
    }

    /* ── אדמין: עדכון סטטוס + תגובה ── */
    @PutMapping("/admin/complaints/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        ComplaintStatus status = ComplaintStatus.valueOf(body.get("status"));
        String response = body.get("response");
        return ResponseEntity.ok(toMap(complaintService.updateStatus(id, status, response)));
    }

    /* ממפה תלונה ל-JSON בטוח (בלי חשיפת סיסמאות של המשתמש) */
    private Map<String, Object> toMap(Complaint c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("subject", c.getSubject());
        m.put("description", c.getDescription());
        m.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        m.put("adminResponse", c.getAdminResponse());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        m.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().toString() : null);
        if (c.getComplainant() != null) {
            m.put("complainantName", c.getComplainant().getFullName());
            m.put("complainantEmail", c.getComplainant().getEmail());
            m.put("complainantRole", c.getComplainant().getRole() != null ? c.getComplainant().getRole().name() : null);
        }
        if (c.getBooking() != null) {
            m.put("bookingId", c.getBooking().getId());
            m.put("bookingService", c.getBooking().getServiceType());
        }
        return m;
    }
}
