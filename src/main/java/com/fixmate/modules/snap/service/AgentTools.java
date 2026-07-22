package com.fixmate.modules.snap.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.availability.model.ProAvailability;
import com.fixmate.modules.availability.service.AvailabilityService;
import com.fixmate.modules.booking.dto.BookingRequest;
import com.fixmate.modules.booking.model.Booking;
import com.fixmate.modules.booking.service.BookingService;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.service.ProService;
import com.fixmate.modules.rating.dto.RatingRequest;
import com.fixmate.modules.rating.service.RatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * הפעולות שהסוכן רשאי לבצע במערכת.
 *
 * כל מתודה כאן נקראת בשם המשתמש המחובר בלבד — ה-User מגיע מהבקר,
 * לא מהמודל, כדי שהסוכן לא יוכל לפעול בשם מישהו אחר.
 */
@Service
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private final ProService proService;
    private final BookingService bookingService;
    private final RatingService ratingService;
    private final AvailabilityService availabilityService;

    public AgentTools(ProService proService, BookingService bookingService,
                      RatingService ratingService, AvailabilityService availabilityService) {
        this.proService = proService;
        this.bookingService = bookingService;
        this.ratingService = ratingService;
        this.availabilityService = availabilityService;
    }

    /** ההגדרות שנשלחות ל-OpenAI — מה הסוכן רשאי לעשות ואילו פרמטרים צריך */
    public List<Map<String, Object>> definitions() {
        return List.of(
            tool("search_professionals",
                 "Find professionals for a trade in a city. Do NOT call this on the user's first "
                 + "message — first understand the problem and ask which city they are in. "
                 + "Both arguments are required; never guess the city.",
                 Map.of(
                     "specialty", enumParam("Trade to search for",
                         "electrical", "plumbing", "ac", "painting",
                         "carpentry", "cleaning", "locksmith", "appliances"),
                     "location",  param("string", "City the user stated. Ask them if they have not said it yet.")
                 ),
                 List.of("specialty", "location")),

            tool("get_my_orders",
                 "Returns the user's bookings split into activeOrders (still cancellable) and "
                 + "pastOrders (completed or cancelled). For cancelling or rescheduling, only "
                 + "offer activeOrders. Use for 'where is my pro?' and 'what's happening with my order?'.",
                 Map.of(),
                 List.of()),

            tool("check_availability",
                 "Check whether a professional works at a requested date-time, and if not, "
                 + "which other professionals of the same trade are available then. "
                 + "ALWAYS call this before create_booking and before reschedule_booking, "
                 + "and present the alternatives it returns.",
                 Map.of(
                     "proId",      param("integer", "the professional the user picked"),
                     "specialty",  enumParam("trade, to find alternatives",
                         "electrical", "plumbing", "ac", "painting",
                         "carpentry", "cleaning", "locksmith", "appliances"),
                     "scheduledAt", param("string", "requested ISO date-time, e.g. 2026-07-23T15:00:00")
                 ),
                 List.of("proId", "specialty", "scheduledAt")),

            tool("create_booking",
                 "Book a professional. NEVER call this until the user has explicitly confirmed the specific pro, date and time.",
                 Map.of(
                     "proId",       param("integer", "id from search_professionals"),
                     "serviceType", param("string",  "Short description of the job"),
                     "scheduledAt", param("string",  "ISO date-time, e.g. 2026-07-21T10:00:00"),
                     "address",     param("string",  "Street address for the visit"),
                     "notes",       param("string",  "Urgency, and any detail the pro should know")
                 ),
                 List.of("proId", "serviceType", "scheduledAt", "address")),

            tool("cancel_booking",
                 "Cancel a booking. Call get_my_orders first, then read the exact booking back to the "
                 + "user and wait for an explicit yes. NEVER cancel without that confirmation.",
                 Map.of(
                     "bookingId", param("integer", "id from get_my_orders"),
                     "reason",    param("string",  "Why the user is cancelling, if they said")
                 ),
                 List.of("bookingId")),

            tool("reschedule_booking",
                 "Change the date, time or address of an existing booking. Confirm the new details "
                 + "with the user before calling.",
                 Map.of(
                     "bookingId",   param("integer", "id from get_my_orders"),
                     "scheduledAt", param("string",  "New ISO date-time"),
                     "address",     param("string",  "New address, only if it changed"),
                     "notes",       param("string",  "Updated notes, only if they changed")
                 ),
                 List.of("bookingId", "scheduledAt")),

            tool("rate_professional",
                 "Save the user's rating once a job is COMPLETED. Ask for a score 1-5 first.",
                 Map.of(
                     "bookingId", param("integer", "id of the completed booking"),
                     "score",     param("integer", "1 to 5"),
                     "comment",   param("string",  "Short free-text review, if the user gave one")
                 ),
                 List.of("bookingId", "score"))
        );
    }

    /** מריץ כלי לפי שם ומחזיר תוצאה שתחזור למודל */
    public Object run(String name, Map<String, Object> args, User user) {
        try {
            return switch (name) {
                case "search_professionals" -> searchPros(str(args, "specialty"), str(args, "location"));
                case "check_availability"   -> checkAvailability(args);
                case "get_my_orders"        -> myOrders(user);
                case "create_booking"       -> createBooking(args, user);
                case "cancel_booking"       -> cancelBooking(args, user);
                case "reschedule_booking"   -> rescheduleBooking(args, user);
                case "rate_professional"    -> rate(args, user);
                default -> Map.of("error", "Unknown tool: " + name);
            };
        } catch (Exception e) {
            log.warn("Agent tool '{}' failed: {}", name, e.getMessage());
            return Map.of("error", e.getMessage() == null ? "Tool failed" : e.getMessage());
        }
    }

    /**
     * כל בעלי המקצוע המאושרים, בפורמט קומפקטי. מוזרק לכל שיחה כדי שהסוכן
     * תמיד יחזיק את ה-proId האמיתי — כך אינו נאלץ לחפש שוב בכל תור,
     * וזה מה שמנע את סגירת ההזמנה (ה-proId היה אבד בין תורות).
     */
    public List<Map<String, Object>> allApprovedProsCompact() {
        List<ProProfile> all = proService.searchPros(null, null);
        if (all == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProProfile p : all) {
            if (p.getUser() == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("proId", p.getUser().getId());   // מזהה User — זה מה ש-createBooking צריך
            m.put("name", p.getUser().getFullName());
            m.put("specialty", p.getSpecialty());
            m.put("location", p.getLocation());
            m.put("priceRange", priceRange(p));
            m.put("rating", p.getAverageRating());
            m.put("workingHours", weeklyHoursText(p.getUser().getId()));  // כדי שלא ימציא שעות
            out.add(m);
        }
        return out;
    }

    /* ── הכלים עצמם ── */

    /**
     * מילים נרדפות לכל תחום. בעלי מקצוע רושמים את ההתמחות בעברית או באנגלית,
     * ולכן חיפוש על מונח אחד בלבד מפספס. מקביל ל-CAT_MATCH שבפרונט.
     */
    private static final Map<String, List<String>> SPECIALTY_SYNONYMS = Map.of(
        "electrical", List.of("electric", "חשמל"),
        "plumbing",   List.of("plumb", "אינסטלצ", "שרברב", "צנרת"),
        "ac",         List.of("ac", "hvac", "מזגן", "מיזוג"),
        "painting",   List.of("paint", "צבע"),
        "carpentry",  List.of("carpent", "נגר"),
        "cleaning",   List.of("clean", "ניקיון"),
        "locksmith",  List.of("lock", "מנעול"),
        "appliances", List.of("applianc", "מכשיר", "מוצרי חשמל")
    );

    private Object searchPros(String specialty, String location) {
        // מושכים את כל המאושרים ומסננים כאן, כדי שנוכל להתאים גם עברית וגם
        // אנגלית — ולסנן לפי תחום ועיר יחד, מה שהמאגר לא תומך בו.
        List<ProProfile> all = proService.searchPros(null, null);
        if (all == null) all = List.of();

        List<String> terms = SPECIALTY_SYNONYMS.getOrDefault(
                specialty == null ? "" : specialty.toLowerCase().trim(),
                specialty == null || specialty.isBlank() ? List.of() : List.of(specialty.toLowerCase()));

        List<ProProfile> found = all.stream()
            .filter(p -> terms.isEmpty() || matches(p.getSpecialty(), terms))
            .filter(p -> cityMatches(p.getLocation(), location))
            .toList();

        // אם העיר צמצמה לאפס — מציעים למודל להרחיב, במקום להצהיר שאין אף אחד
        if (found.isEmpty() && location != null && !location.isBlank()) {
            List<ProProfile> anywhere = all.stream()
                .filter(p -> terms.isEmpty() || matches(p.getSpecialty(), terms))
                .toList();
            if (!anywhere.isEmpty()) {
                return Map.of(
                    "results", List.of(),
                    "note", "None in " + location + ", but " + anywhere.size()
                          + " available elsewhere. Offer to search without the city."
                );
            }
        }

        if (found.isEmpty()) {
            return Map.of("results", List.of(),
                          "note", "No approved professionals registered for this trade.");
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (ProProfile p : found.stream().limit(5).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            // proId חייב להיות מזהה ה-User (לא מזהה הפרופיל), כי createBooking
            // מחפש לפי User id — בדיוק כמו שהמסך שולח modal.userId
            m.put("proId", p.getUser() != null ? p.getUser().getId() : null);
            m.put("name", p.getUser() != null ? p.getUser().getFullName() : "");
            m.put("specialty", p.getSpecialty());
            m.put("location", p.getLocation());
            m.put("priceRange", priceRange(p));
            m.put("rating", p.getAverageRating());
            m.put("yearsExperience", p.getYearsExperience());
            out.add(m);
        }
        return Map.of("results", out);
    }

    /**
     * בודק אם בעל המקצוע שנבחר עובד במועד המבוקש. אם לא — מחזיר רשימת
     * בעלי מקצוע אחרים מאותו תחום שכן עובדים אז. משמש לפני הזמנה ושינוי מועד.
     */
    private Object checkAvailability(Map<String, Object> args) {
        Long proId = asLong(args.get("proId"));
        String specialty = str(args, "specialty");

        LocalDateTime when;
        try {
            when = LocalDateTime.parse(str(args, "scheduledAt"));
        } catch (DateTimeParseException | NullPointerException e) {
            return Map.of("error", "scheduledAt must be ISO format like 2026-07-23T15:00:00");
        }

        // פנוי = גם בשעות העבודה (רמה א') וגם בלי הזמנה מתנגשת (רמה ב')
        boolean chosenFree = worksAt(proId, when) && !hasClash(proId, when);
        if (chosenFree) {
            return Map.of("available", true, "proId", proId);
        }

        // סיבת אי-הזמינות — הודעה ברורה לפי הסיבה האמיתית, כדי שהסוכן לא
        // יבלבל בין "מחוץ לשעות" (לצטט שעות) לבין "כבר תפוס" (לא לצטט שעות)
        boolean outsideHours = !worksAt(proId, when);
        String reason = outsideHours
                ? "The pro does not work then. Their working hours that day are "
                  + workingHoursText(proId, when) + "."
                : "The pro already has another appointment at that time (they are "
                  + "within working hours, just booked).";

        // בעל המקצוע שנבחר לא פנוי אז — מחפשים חלופות מאותו תחום
        List<String> terms = SPECIALTY_SYNONYMS.getOrDefault(
                specialty == null ? "" : specialty.toLowerCase().trim(),
                specialty == null || specialty.isBlank() ? List.of() : List.of(specialty.toLowerCase()));

        List<Map<String, Object>> alternatives = new ArrayList<>();
        List<ProProfile> all = proService.searchPros(null, null);
        if (all != null) {
            for (ProProfile p : all) {
                if (p.getUser() == null) continue;
                Long uid = p.getUser().getId();
                if (uid.equals(proId)) continue;                       // לא את מי שכבר נבחר
                if (!terms.isEmpty() && !matches(p.getSpecialty(), terms)) continue;
                if (!worksAt(uid, when) || hasClash(uid, when)) continue;  // רק מי שבאמת פנוי אז
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("proId", uid);
                m.put("name", p.getUser().getFullName());
                m.put("location", p.getLocation());
                m.put("priceRange", priceRange(p));
                m.put("rating", p.getAverageRating());
                m.put("workingHours", workingHoursText(uid, when));
                alternatives.add(m);
                if (alternatives.size() >= 5) break;
            }
        }

        return Map.of(
            "available", false,
            "reason", reason,
            "alternatives", alternatives
        );
    }

    /**
     * האם לבעל המקצוע כבר יש הזמנה פעילה שמתנגשת עם המועד המבוקש.
     * מתייחסים להזמנה פעילה (ממתינה/מאושרת) שמתחילה בתוך שעה מהמועד המבוקש
     * כהתנגשות — הנחת ביקור של כשעה.
     */
    private boolean hasClash(Long proUserId, LocalDateTime when) {
        if (proUserId == null) return false;
        List<Booking> proBookings = bookingService.getProBookings(proUserId);
        if (proBookings == null) return false;
        for (Booking b : proBookings) {
            String st = String.valueOf(b.getStatus());
            boolean active = "PENDING".equals(st) || "CONFIRMED".equals(st);
            if (!active || b.getScheduledAt() == null) continue;
            long gapMinutes = Math.abs(
                java.time.Duration.between(b.getScheduledAt(), when).toMinutes());
            if (gapMinutes < 60) return true;   // פחות משעה הפרש = חופף
        }
        return false;
    }

    /** האם בעל המקצוע עובד ביום ובשעה של המועד הנתון */
    private boolean worksAt(Long proUserId, LocalDateTime when) {
        if (proUserId == null) return false;
        String day = when.getDayOfWeek().name();   // SUNDAY, MONDAY, ...
        LocalTime t = when.toLocalTime();
        List<ProAvailability> slots = availabilityService.getAvailability(proUserId);
        if (slots == null) return false;
        for (ProAvailability s : slots) {
            if (!day.equalsIgnoreCase(s.getDayOfWeek())) continue;
            if (!s.isAvailable()) continue;
            if (s.getStartTime() == null || s.getEndTime() == null) continue;
            if (!t.isBefore(s.getStartTime()) && !t.isAfter(s.getEndTime())) return true;
        }
        return false;
    }

    /** טווח שעות העבודה השבועי (טיפוסי) של בעל המקצוע, לצורך ההזרקה לסוכן */
    private String weeklyHoursText(Long proUserId) {
        for (ProAvailability s : availabilityService.getAvailability(proUserId)) {
            if (s.isAvailable() && s.getStartTime() != null && s.getEndTime() != null) {
                return s.getStartTime() + "-" + s.getEndTime();
            }
        }
        return "unknown";
    }

    /** תיאור שעות העבודה של בעל המקצוע ביום המבוקש, לצורך הודעה למשתמש */
    private String workingHoursText(Long proUserId, LocalDateTime when) {
        String day = when.getDayOfWeek().name();
        for (ProAvailability s : availabilityService.getAvailability(proUserId)) {
            if (day.equalsIgnoreCase(s.getDayOfWeek()) && s.isAvailable()
                    && s.getStartTime() != null && s.getEndTime() != null) {
                return s.getStartTime() + "-" + s.getEndTime();
            }
        }
        return "not working that day";
    }

    /** האם ההתמחות הרשומה מכילה אחת מהמילים הנרדפות */
    private static boolean matches(String proSpecialty, List<String> terms) {
        if (proSpecialty == null || proSpecialty.isBlank()) return false;
        String s = proSpecialty.toLowerCase();
        return terms.stream().anyMatch(t -> s.contains(t.toLowerCase()));
    }

    /**
     * ערים בעברית ובאנגלית. בעל מקצוע עשוי לרשום "Tel Aviv" בעוד הלקוח כותב
     * "תל אביב", ולכן משווים גם מול השם המקביל.
     */
    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("תל אביב", "tel aviv"),   Map.entry("tel aviv", "תל אביב"),
        Map.entry("ירושלים", "jerusalem"),  Map.entry("jerusalem", "ירושלים"),
        Map.entry("חיפה", "haifa"),         Map.entry("haifa", "חיפה"),
        Map.entry("נצרת", "nazareth"),      Map.entry("nazareth", "נצרת"),
        Map.entry("באר שבע", "beer sheva"), Map.entry("beer sheva", "באר שבע"),
        Map.entry("ראשון לציון", "rishon"), Map.entry("rishon", "ראשון לציון"),
        Map.entry("פתח תקווה", "petah"),    Map.entry("petah", "פתח תקווה"),
        Map.entry("אשדוד", "ashdod"),       Map.entry("ashdod", "אשדוד"),
        Map.entry("נתניה", "netanya"),      Map.entry("netanya", "נתניה"),
        Map.entry("חולון", "holon"),        Map.entry("holon", "חולון"),
        Map.entry("רמת גן", "ramat gan"),   Map.entry("ramat gan", "רמת גן"),
        Map.entry("אכסאל", "iksal"),        Map.entry("iksal", "אכסאל"),
        Map.entry("קריות", "krayot"),       Map.entry("krayot", "קריות"),
        Map.entry("עפולה", "afula"),        Map.entry("afula", "עפולה"),
        Map.entry("נשר", "nesher"),         Map.entry("nesher", "נשר")
    );

    /** האם אזור השירות של בעל המקצוע כולל את העיר שהלקוח ציין */
    private static boolean cityMatches(String proLocation, String wanted) {
        if (wanted == null || wanted.isBlank()) return true;
        if (proLocation == null || proLocation.isBlank()) return false;

        String area = proLocation.toLowerCase().trim();
        String city = wanted.toLowerCase().trim();
        if (area.contains(city)) return true;

        String alias = CITY_ALIASES.get(city);
        return alias != null && area.contains(alias);
    }

    private Object myOrders(User user) {
        List<Booking> bookings = bookingService.getClientBookings(user.getId());
        List<Map<String, Object>> active = new ArrayList<>();
        List<Map<String, Object>> past = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        for (Booking b : bookings) {
            String status = String.valueOf(b.getStatus());
            boolean done = "COMPLETED".equals(status) || "CANCELLED".equals(status);
            boolean timePassed = b.getScheduledAt() != null && b.getScheduledAt().isBefore(now);
            boolean finished = done || timePassed;   // עבר זמנה = כבר לא רלוונטית לביטול

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bookingId", b.getId());
            m.put("serviceType", b.getServiceType());
            m.put("status", status);
            m.put("scheduledAt", String.valueOf(b.getScheduledAt()));
            m.put("address", b.getAddress());
            m.put("proName", b.getPro() != null ? b.getPro().getFullName() : null);
            m.put("proPhone", b.getPro() != null ? b.getPro().getPhone() : null);
            m.put("finalPrice", b.getTotalPrice());   // נקבע רק בסיום העבודה
            // ניתן לבטל/לשנות רק הזמנה פעילה (ממתינה או מאושרת) שעדיין לא הסתיימה
            m.put("cancellable", !finished);

            (finished ? past : active).add(m);
        }

        // מפרידים: הזמנות פעילות הן אלה שרלוונטיות לביטול/שינוי. את שהסתיימו
        // מחזירים בנפרד כדי שהסוכן לא יציע לבטל הזמנה שכבר עברה או בוטלה.
        return Map.of("activeOrders", active, "pastOrders", past);
    }

    private Object cancelBooking(Map<String, Object> args, User user) {
        Long id = asLong(args.get("bookingId"));
        if (id == null) return Map.of("error", "bookingId is required");
        bookingService.cancelBooking(id, user, str(args, "reason"));
        return Map.of("cancelled", true, "bookingId", id);
    }

    private Object rescheduleBooking(Map<String, Object> args, User user) {
        Long id = asLong(args.get("bookingId"));
        if (id == null) return Map.of("error", "bookingId is required");

        LocalDateTime when;
        try {
            when = LocalDateTime.parse(str(args, "scheduledAt"));
        } catch (DateTimeParseException | NullPointerException e) {
            return Map.of("error", "scheduledAt must be ISO format like 2026-07-21T10:00:00");
        }

        Booking updated = bookingService.editBooking(
                id, user, when, str(args, "address"), str(args, "notes"));

        return Map.of(
            "rescheduled", true,
            "bookingId", updated.getId(),
            "scheduledAt", String.valueOf(updated.getScheduledAt())
        );
    }

    private Object rate(Map<String, Object> args, User user) {
        Long id = asLong(args.get("bookingId"));
        Long score = asLong(args.get("score"));
        if (id == null || score == null) return Map.of("error", "bookingId and score are required");
        if (score < 1 || score > 5) return Map.of("error", "score must be between 1 and 5");

        RatingRequest req = new RatingRequest();
        req.setBookingId(id);
        req.setScore(score.intValue());
        req.setComment(str(args, "comment"));

        ratingService.rateBooking(user, req);
        return Map.of("rated", true, "bookingId", id, "score", score);
    }

    private Object createBooking(Map<String, Object> args, User user) {
        LocalDateTime when;
        try {
            when = LocalDateTime.parse(str(args, "scheduledAt"));
        } catch (DateTimeParseException | NullPointerException e) {
            return Map.of("error", "scheduledAt must be ISO format like 2026-07-21T10:00:00");
        }

        BookingRequest req = new BookingRequest();
        req.setProId(asLong(args.get("proId")));
        req.setServiceType(str(args, "serviceType"));
        req.setScheduledAt(when);
        req.setAddress(str(args, "address"));
        req.setNotes(str(args, "notes"));

        Booking saved = bookingService.createBooking(user, req);
        return Map.of(
            "booked", true,
            "bookingId", saved.getId(),
            "scheduledAt", String.valueOf(saved.getScheduledAt())
        );
    }

    /* ── עזר ── */

    private String priceRange(ProProfile p) {
        if (p.getHourlyRate() == null) return "not set";
        if (p.getHourlyRateMax() == null) return p.getHourlyRate() + " ILS/hr";
        return p.getHourlyRate() + "-" + p.getHourlyRateMax() + " ILS/hr";
    }

    private static Map<String, Object> tool(String name, String description,
                                            Map<String, Object> props, List<String> required) {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", name,
                "description", description,
                "parameters", Map.of(
                    "type", "object",
                    "properties", props,
                    "required", required
                )
            )
        );
    }

    private static Map<String, Object> param(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    /** פרמטר עם רשימת ערכים סגורה — מונע מהמודל להמציא מונח שלא קיים ב-DB */
    private static Map<String, Object> enumParam(String description, String... values) {
        return Map.of("type", "string", "description", description, "enum", List.of(values));
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v).trim());
    }
}
