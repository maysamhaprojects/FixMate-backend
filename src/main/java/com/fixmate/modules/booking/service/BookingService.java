package com.fixmate.modules.booking.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.modules.booking.dto.BookingRequest;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.common.email.EmailService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public BookingService(BookingRepository bookingRepository, UserRepository userRepository, EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public Booking createBooking(User client, BookingRequest req) {
        User pro = userRepository.findById(req.getProId())
                .orElseThrow(() -> new RuntimeException("Professional not found"));

        Booking booking = new Booking();
        booking.setClient(client);
        booking.setPro(pro);
        booking.setServiceType(req.getServiceType());
        booking.setScheduledAt(req.getScheduledAt());
        booking.setAddress(req.getAddress());
        booking.setNotes(req.getNotes());
        booking.setStatus(BookingStatus.PENDING);

        Booking saved = bookingRepository.save(booking);

        // מייל לבעל המקצוע — הזמנה חדשה
        emailService.send(pro.getEmail(),
            "FixMate — הזמנה חדשה!",
            "שלום " + pro.getFullName() + ",\n\n" +
            "קיבלת הזמנה חדשה מ-" + client.getFullName() + ".\n" +
            "שירות: " + req.getServiceType() + "\n" +
            "מועד: " + req.getScheduledAt() + "\n" +
            "כתובת: " + req.getAddress() + "\n\n" +
            "היכנס ל-FixMate כדי לאשר את ההזמנה.\n\nצוות FixMate");

        // מייל ללקוח — ההזמנה נשלחה
        emailService.send(client.getEmail(),
            "FixMate — ההזמנה שלך נשלחה",
            "שלום " + client.getFullName() + ",\n\n" +
            "הזמנתך אצל " + pro.getFullName() + " נשלחה וממתינה לאישור בעל המקצוע.\n" +
            "שירות: " + req.getServiceType() + "\n" +
            "מועד: " + req.getScheduledAt() + "\n\n" +
            "נעדכן אותך כשההזמנה תאושר.\n\nצוות FixMate");

        return saved;
    }

    public List<Booking> getClientBookings(Long clientId) {
        return bookingRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    public List<Booking> getProBookings(Long proId) {
        return bookingRepository.findByProIdOrderByCreatedAtDesc(proId);
    }

    public Booking updateStatus(Long bookingId, BookingStatus newStatus, User requester) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isPro = booking.getPro().getId().equals(requester.getId());
        boolean isClient = booking.getClient().getId().equals(requester.getId());

        if (!isPro && !isClient) {
            throw new RuntimeException("Unauthorized");
        }

        booking.setStatus(newStatus);
        Booking saved = bookingRepository.save(booking);

        // מייל ללקוח על עדכון הסטטוס
        User client = booking.getClient();
        User pro = booking.getPro();
        if (client != null) {
            String proName = pro != null ? pro.getFullName() : "בעל המקצוע";
            String line = switch (newStatus) {
                case CONFIRMED   -> "ההזמנה שלך אושרה על ידי " + proName + "!";
                case IN_PROGRESS -> proName + " התחיל לטפל בהזמנה שלך.";
                case COMPLETED   -> "העבודה עם " + proName + " הושלמה. אפשר לדרג את השירות ב-FixMate!";
                case CANCELLED   -> "ההזמנה שלך בוטלה.";
                default          -> null;
            };
            if (line != null) {
                emailService.send(client.getEmail(),
                    "FixMate — עדכון בהזמנה שלך",
                    "שלום " + client.getFullName() + ",\n\n" + line + "\n\nצוות FixMate");
            }
        }
        return saved;
    }

    public Booking editBooking(Long bookingId, User client,
                               java.time.LocalDateTime scheduledAt, String address, String notes) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        // אפשר לערוך רק כל עוד ההזמנה ממתינה לאישור בעל המקצוע
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new RuntimeException("Only pending orders can be edited");
        }

        if (scheduledAt != null) booking.setScheduledAt(scheduledAt);
        if (address != null && !address.isBlank()) booking.setAddress(address.trim());
        booking.setNotes(notes); // אפשר גם לרוקן הערות

        Booking saved = bookingRepository.save(booking);

        // מייל לבעל המקצוע — ההזמנה עודכנה
        User pro = booking.getPro();
        if (pro != null) {
            emailService.send(pro.getEmail(),
                "FixMate — הזמנה עודכנה",
                "שלום " + pro.getFullName() + ",\n\n" +
                "הלקוח " + client.getFullName() + " עדכן פרטים בהזמנה שממתינה לאישורך.\n" +
                "שירות: " + booking.getServiceType() + "\n" +
                "מועד מעודכן: " + booking.getScheduledAt() + "\n" +
                "כתובת מעודכנת: " + booking.getAddress() + "\n\n" +
                "היכנס ל-FixMate כדי לצפות ולאשר.\n\nצוות FixMate");
        }
        return saved;
    }

    public void cancelBooking(Long bookingId, User client, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) booking.setCancellationReason(reason.trim());
        bookingRepository.save(booking);

        // מייל לבעל המקצוע על הביטול (כולל הסיבה)
        User pro = booking.getPro();
        if (pro != null) {
            emailService.send(pro.getEmail(),
                "FixMate — הזמנה בוטלה",
                "שלום " + pro.getFullName() + ",\n\n" +
                "ההזמנה של " + client.getFullName() + " בוטלה.\n" +
                (reason != null && !reason.isBlank() ? ("סיבת הביטול: " + reason.trim() + "\n") : "") +
                "\nצוות FixMate");
        }
    }
}
