package com.fixmate.modules.booking.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.modules.availability.model.ProAvailability;
import com.fixmate.modules.availability.service.AvailabilityService;
import com.fixmate.modules.booking.dto.BookingRequest;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.model.BookingStatus;
import com.fixmate.modules.booking.repository.BookingRepository;
import com.fixmate.common.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AvailabilityService availabilityService;

    public BookingService(BookingRepository bookingRepository, UserRepository userRepository,
                          EmailService emailService, AvailabilityService availabilityService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.availabilityService = availabilityService;
    }

    public Booking createBooking(User client, BookingRequest req) {
        User pro = userRepository.findById(req.getProId())
                .orElseThrow(() -> new RuntimeException("Professional not found"));

        // אסור לקבוע מועד שכבר עבר
        requireFutureDate(req.getScheduledAt());

        // בדיקת זמינות — בעל המקצוע חייב לעבוד במועד המבוקש ולא להיות תפוס.
        // (אותה בדיקה כמו בשינוי מועד; להזמנה חדשה אין hasClash לדלג עליה.)
        if (req.getScheduledAt() != null) {
            if (!worksAt(pro.getId(), req.getScheduledAt())) {
                throw new RuntimeException("בעל המקצוע לא עובד במועד שבחרת. אנא בחרו מועד אחר.");
            }
            if (hasClash(pro.getId(), req.getScheduledAt(), null)) {
                throw new RuntimeException("בעל המקצוע כבר תפוס במועד הזה. אנא בחרו מועד אחר.");
            }
        }

        Booking booking = new Booking();
        booking.setClient(client);
        booking.setPro(pro);
        booking.setServiceType(req.getServiceType());
        booking.setScheduledAt(req.getScheduledAt());
        booking.setAddress(req.getAddress());
        booking.setNotes(req.getNotes());
        booking.setStatus(BookingStatus.PENDING);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking #{} created: client {} -> pro {}, service '{}'",
                saved.getId(), client.getId(), pro.getId(), req.getServiceType());

        // מייל לבעל המקצוע — הזמנה חדשה
        emailService.send(pro.getEmail(),
            "FixMate — התקבלה הזמנה חדשה 🔔",
            "שלום " + pro.getFullName() + ",\n\n" +
            "התקבלה עבורך הזמנת שירות חדשה דרך FixMate. 🎉\n\n" +
            "📋 פרטי ההזמנה:\n" +
            "👤 לקוח: " + client.getFullName() + "\n" +
            "🛠️ שירות: " + req.getServiceType() + "\n" +
            "📅 מועד מבוקש: " + fmtDate(req.getScheduledAt()) + "\n" +
            "📍 כתובת: " + req.getAddress() + "\n\n" +
            "יש להיכנס למערכת FixMate כדי לאשר או לדחות את ההזמנה. ✅\n\n" +
            "בברכה,\n🔧 צוות FixMate");

        // מייל ללקוח — ההזמנה נשלחה
        emailService.send(client.getEmail(),
            "FixMate — הזמנתך התקבלה ✅",
            "שלום " + client.getFullName() + ",\n\n" +
            "תודה שבחרת ב-FixMate! 🎉 הזמנתך נשלחה לבעל המקצוע וממתינה לאישורו.\n\n" +
            "📋 פרטי ההזמנה:\n" +
            "👤 בעל מקצוע: " + pro.getFullName() + "\n" +
            "🛠️ שירות: " + req.getServiceType() + "\n" +
            "📅 מועד מבוקש: " + fmtDate(req.getScheduledAt()) + "\n\n" +
            "נעדכן אותך במייל ברגע שההזמנה תאושר. 🔔\n\n" +
            "בברכה,\n🔧 צוות FixMate");

        return saved;
    }

    public List<Booking> getClientBookings(Long clientId) {
        return bookingRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    public List<Booking> getProBookings(Long proId) {
        return bookingRepository.findByProIdOrderByCreatedAtDesc(proId);
    }

    /** פורמט תאריך קריא למיילים: "30/07/2026 בשעה 17:00" (במקום ה-ISO עם ה-T). */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'בשעה' HH:mm");
    private String fmtDate(LocalDateTime dt) {
        return dt == null ? "—" : dt.format(DATE_FMT);
    }

    /** מוודא שהמועד המבוקש אינו בעבר — אחרת זורק שגיאה ברורה. */
    private void requireFutureDate(LocalDateTime scheduledAt) {
        if (scheduledAt != null && scheduledAt.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("לא ניתן לקבוע מועד שכבר עבר. אנא בחרו תאריך ושעה עתידיים.");
        }
    }

    /**
     * האם בעל המקצוע עובד ביום ובשעה הנתונים, לפי לוח הזמינות שלו.
     * בעל מקצוע שלא הגדיר לוח כלל אינו מגביל את שעותיו — נחשב זמין.
     */
    private boolean worksAt(Long proUserId, LocalDateTime when) {
        if (proUserId == null) return false;
        List<ProAvailability> slots = availabilityService.getAvailability(proUserId);
        if (slots == null || slots.isEmpty()) return true;
        String day = when.getDayOfWeek().name();   // SUNDAY, MONDAY, ...
        LocalTime t = when.toLocalTime();
        for (ProAvailability s : slots) {
            if (!day.equalsIgnoreCase(s.getDayOfWeek())) continue;
            if (!s.isAvailable()) continue;
            if (s.getStartTime() == null || s.getEndTime() == null) continue;
            if (!t.isBefore(s.getStartTime()) && !t.isAfter(s.getEndTime())) return true;
        }
        return false;
    }

    /**
     * האם לבעל המקצוע כבר יש הזמנה פעילה (ממתינה/מאושרת) שמתנגשת עם המועד
     * (פחות משעה הפרש). מדלגים על ההזמנה שנערכת עצמה כדי שלא תתנגש עם עצמה.
     */
    private boolean hasClash(Long proUserId, LocalDateTime when, Long excludeBookingId) {
        if (proUserId == null) return false;
        List<Booking> proBookings = getProBookings(proUserId);
        if (proBookings == null) return false;
        for (Booking b : proBookings) {
            if (excludeBookingId != null && excludeBookingId.equals(b.getId())) continue;
            String st = String.valueOf(b.getStatus());
            boolean active = "PENDING".equals(st) || "CONFIRMED".equals(st);
            if (!active || b.getScheduledAt() == null) continue;
            long gap = Math.abs(Duration.between(b.getScheduledAt(), when).toMinutes());
            if (gap < 60) return true;
        }
        return false;
    }

    /**
     * האם בעל המקצוע פנוי במועד נתון — גם עובד אז (worksAt) וגם בלי הזמנה
     * מתנגשת (hasClash). משמש לסינון תוצאות החיפוש למועד שהלקוח בחר, כדי
     * שלא יוצגו מקצוענים שממילא ייחסמו ביצירת ההזמנה.
     */
    public boolean isAvailableAt(Long proUserId, LocalDateTime when) {
        return worksAt(proUserId, when) && !hasClash(proUserId, when, null);
    }

    public Booking updateStatus(Long bookingId, BookingStatus newStatus, Double finalPrice, User requester) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isPro = booking.getPro().getId().equals(requester.getId());
        boolean isClient = booking.getClient().getId().equals(requester.getId());

        if (!isPro && !isClient) {
            throw new RuntimeException("Unauthorized");
        }

        booking.setStatus(newStatus);
        // בסיום העבודה — בעל המקצוע קובע את המחיר הסופי של ההזמנה
        if (newStatus == BookingStatus.COMPLETED && finalPrice != null) {
            booking.setTotalPrice(finalPrice);
        }
        Booking saved = bookingRepository.save(booking);
        log.info("Booking #{} status changed to {} by user {}", bookingId, newStatus, requester.getId());

        // מייל ללקוח על עדכון הסטטוס
        User client = booking.getClient();
        User pro = booking.getPro();
        if (client != null) {
            String proName = pro != null ? pro.getFullName() : "בעל המקצוע";
            String line = switch (newStatus) {
                case CONFIRMED   -> "✅ הזמנתך אושרה על ידי " + proName + "! נתראה במועד שנקבע.";
                case IN_PROGRESS -> "🔧 " + proName + " התחיל לטפל בהזמנה שלך.";
                case COMPLETED   -> "🎉 העבודה עם " + proName + " הושלמה"
                                    + (booking.getTotalPrice() != null ? ".\n💰 המחיר הסופי: ₪" + booking.getTotalPrice() : "")
                                    + "\n\n⭐ נשמח אם תדרג/י את השירות ב-FixMate — זה עוזר ללקוחות אחרים!";
                case CANCELLED   -> "❌ הזמנתך בוטלה.";
                default          -> null;
            };
            if (line != null) {
                emailService.send(client.getEmail(),
                    "FixMate — עדכון בהזמנה שלך 🔔",
                    "שלום " + client.getFullName() + ",\n\n" + line + "\n\nבברכה,\n🔧 צוות FixMate");
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

        // אסור לקבוע מועד שכבר עבר
        requireFutureDate(scheduledAt);

        // בדיקת זמינות למועד החדש — בעל המקצוע חייב לעבוד אז ולא להיות תפוס.
        // (מדלגים על ההזמנה הנוכחית בבדיקת ההתנגשות כדי שלא תתנגש עם עצמה.)
        User proForCheck = booking.getPro();
        if (scheduledAt != null && proForCheck != null) {
            Long proUserId = proForCheck.getId();
            if (!worksAt(proUserId, scheduledAt)) {
                throw new RuntimeException("בעל המקצוע לא עובד במועד שבחרת. אנא בחרו מועד אחר.");
            }
            if (hasClash(proUserId, scheduledAt, bookingId)) {
                throw new RuntimeException("בעל המקצוע כבר תפוס במועד הזה. אנא בחרו מועד אחר.");
            }
        }

        if (scheduledAt != null) booking.setScheduledAt(scheduledAt);
        if (address != null && !address.isBlank()) booking.setAddress(address.trim());
        booking.setNotes(notes); // אפשר גם לרוקן הערות

        Booking saved = bookingRepository.save(booking);

        // מייל לבעל המקצוע — ההזמנה עודכנה
        User pro = booking.getPro();
        if (pro != null) {
            emailService.send(pro.getEmail(),
                "FixMate — עדכון בהזמנה ✏️",
                "שלום " + pro.getFullName() + ",\n\n" +
                "הלקוח " + client.getFullName() + " עדכן פרטים בהזמנה הממתינה לאישורך.\n\n" +
                "📋 הפרטים המעודכנים:\n" +
                "🛠️ שירות: " + booking.getServiceType() + "\n" +
                "📅 מועד מעודכן: " + fmtDate(booking.getScheduledAt()) + "\n" +
                "📍 כתובת מעודכנת: " + booking.getAddress() + "\n\n" +
                "יש להיכנס למערכת FixMate כדי לצפות ולאשר. ✅\n\n" +
                "בברכה,\n🔧 צוות FixMate");
        }

        // אישור ללקוח — מה בדיוק נשמר אחרי העדכון
        emailService.send(client.getEmail(),
            "FixMate — הזמנתך עודכנה ✏️",
            "שלום " + client.getFullName() + ",\n\n" +
            "פרטי הזמנתך" + (pro != null ? (" אצל " + pro.getFullName()) : "") + " עודכנו בהצלחה. ✅\n\n" +
            "📋 הפרטים המעודכנים:\n" +
            "🛠️ שירות: " + booking.getServiceType() + "\n" +
            "📅 מועד מעודכן: " + fmtDate(booking.getScheduledAt()) + "\n" +
            "📍 כתובת מעודכנת: " + booking.getAddress() + "\n\n" +
            "ההזמנה ממתינה לאישור בעל המקצוע. 🔔\n\n" +
            "בברכה,\n🔧 צוות FixMate");

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
        log.info("Booking #{} cancelled by client {}", bookingId, client.getId());

        String reasonLine = (reason != null && !reason.isBlank())
                ? ("סיבת הביטול: " + reason.trim() + "\n") : "";

        // מייל לבעל המקצוע על הביטול (כולל הסיבה)
        User pro = booking.getPro();
        if (pro != null) {
            emailService.send(pro.getEmail(),
                "FixMate — הזמנה בוטלה ❌",
                "שלום " + pro.getFullName() + ",\n\n" +
                "❌ ההזמנה של " + client.getFullName() + " בוטלה.\n" +
                (reasonLine.isEmpty() ? "" : "📝 " + reasonLine) +
                "\nבברכה,\n🔧 צוות FixMate");
        }

        // אישור ללקוח — תיעוד הביטול
        emailService.send(client.getEmail(),
            "FixMate — הזמנתך בוטלה ❌",
            "שלום " + client.getFullName() + ",\n\n" +
            "❌ הזמנתך" + (pro != null ? (" אצל " + pro.getFullName()) : "") + " בוטלה.\n\n" +
            "📋 פרטי ההזמנה שבוטלה:\n" +
            "🛠️ שירות: " + booking.getServiceType() + "\n" +
            "📅 מועד שהיה מתוכנן: " + fmtDate(booking.getScheduledAt()) + "\n" +
            (reasonLine.isEmpty() ? "" : "📝 " + reasonLine) +
            "\nבברכה,\n🔧 צוות FixMate");
    }
}
