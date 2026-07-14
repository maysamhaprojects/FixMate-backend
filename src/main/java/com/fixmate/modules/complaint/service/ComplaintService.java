package com.fixmate.modules.complaint.service;

import com.fixmate.common.email.EmailService;
import com.fixmate.modules.auth.model.Role;
import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.modules.complaint.dto.ComplaintRequest;
import com.fixmate.modules.complaint.model.Complaint;
import com.fixmate.modules.complaint.model.ComplaintStatus;
import com.fixmate.modules.complaint.repository.ComplaintRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public ComplaintService(ComplaintRepository complaintRepository,
                            BookingRepository bookingRepository,
                            UserRepository userRepository,
                            EmailService emailService) {
        this.complaintRepository = complaintRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public Complaint create(User complainant, ComplaintRequest req) {
        Complaint c = new Complaint();
        c.setComplainant(complainant);
        c.setSubject(req.getSubject().trim());
        c.setDescription(req.getDescription().trim());
        c.setStatus(ComplaintStatus.OPEN);

        if (req.getBookingId() != null) {
            Booking b = bookingRepository.findById(req.getBookingId()).orElse(null);
            if (b != null) c.setBooking(b);
        }

        Complaint saved = complaintRepository.save(c);

        // מייל לכל האדמינים על תלונה חדשה
        for (User admin : userRepository.findAll()) {
            if (admin.getRole() == Role.ADMIN) {
                emailService.send(admin.getEmail(),
                    "FixMate — תלונה חדשה התקבלה",
                    "שלום " + admin.getFullName() + ",\n\n" +
                    "התקבלה תלונה חדשה מ-" + complainant.getFullName() + " (" + complainant.getEmail() + ").\n" +
                    "נושא: " + saved.getSubject() + "\n" +
                    "תיאור: " + saved.getDescription() + "\n\n" +
                    "היכנס לדשבורד הניהול כדי לטפל.\n\nצוות FixMate");
            }
        }
        return saved;
    }

    public List<Complaint> getMine(Long complainantId) {
        return complaintRepository.findByComplainantIdOrderByCreatedAtDesc(complainantId);
    }

    public List<Complaint> getAll() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    public Complaint updateStatus(Long id, ComplaintStatus status, String adminResponse) {
        Complaint c = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        c.setStatus(status);
        if (adminResponse != null && !adminResponse.isBlank()) c.setAdminResponse(adminResponse.trim());
        if (status == ComplaintStatus.RESOLVED) c.setResolvedAt(LocalDateTime.now());

        Complaint saved = complaintRepository.save(c);

        // מייל למתלונן על עדכון סטטוס התלונה
        User u = c.getComplainant();
        if (u != null) {
            String statusText = switch (status) {
                case OPEN      -> "התלונה שלך נפתחה וממתינה לטיפול.";
                case IN_REVIEW -> "התלונה שלך נמצאת בטיפול.";
                case RESOLVED  -> "התלונה שלך טופלה.";
            };
            emailService.send(u.getEmail(),
                "FixMate — עדכון לגבי התלונה שלך",
                "שלום " + u.getFullName() + ",\n\n" +
                "נושא: " + c.getSubject() + "\n" +
                statusText + "\n" +
                (adminResponse != null && !adminResponse.isBlank() ? ("תגובת הצוות: " + adminResponse.trim() + "\n") : "") +
                "\nצוות FixMate");
        }
        return saved;
    }
}
