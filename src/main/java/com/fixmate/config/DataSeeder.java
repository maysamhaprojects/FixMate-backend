package com.fixmate.config;

import com.fixmate.modules.auth.model.Role;
import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.repository.ProProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * זריעת נתוני התחלה — רצה רק כשמסד הנתונים ריק לגמרי (אפס משתמשים).
 *
 * המטרה: פרויקט שמורידים מ-GitHub ומריצים על DB חדש (למשל בענן) יהיה מיד
 * ניתן לבדיקה — עם חשבון אדמין ועם בעלי מקצוע מאושרים להזמנה.
 *
 * חשוב: כשה-DB כבר מכיל נתונים (למשל אצל המפַתחת) — ה-seeder לא עושה כלום.
 *
 * ⚠️ פרטי ברירת מחדל לפיתוח/הדגמה בלבד — יש לשנותם בסביבת פרודקשן.
 *   אדמין:       admin@fixmate.com  /  Admin@123
 *   בעלי מקצוע:  <email מהרשימה>     /  Pro@123
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final ProProfileRepository proProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      ProProfileRepository proProfileRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.proProfileRepository = proProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // רצים רק על DB ריק — כדי לא לגעת בנתונים קיימים
        if (userRepository.count() > 0) {
            return;
        }

        log.info("Empty database detected — seeding default admin and sample professionals.");

        // ── אדמין ברירת-מחדל ──
        User admin = new User();
        admin.setFullName("FixMate Admin");
        admin.setEmail("admin@fixmate.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin@123")); // מוצפן BCrypt
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        // ── בעלי מקצוע מאושרים — מופיעים בחיפוש ובהזמנה (5 תחומים, 3 ערים) ──
        seedPro("יוסי לוי",   "yosi.electric@fixmate.com",  "0501111111", "electricity", "חיפה",
                150.0, 250.0, 8, "חשמלאי מוסמך עם ניסיון בתקלות ביתיות ולוחות חשמל.", true);
        seedPro("סאמי חורי",  "sami.plumb@fixmate.com",     "0502222222", "plumbing", "תל אביב",
                180.0, 300.0, 10, "שרברב מנוסה — נזילות, סתימות והתקנות.", true);
        seedPro("דוד כהן",    "david.ac@fixmate.com",       "0503333333", "ac", "ירושלים",
                200.0, 350.0, 6, "טכנאי מזגנים — טיפולים, מילוי גז והתקנות.", true);
        seedPro("מריה פרץ",   "maria.paint@fixmate.com",    "0504444444", "painting", "תל אביב",
                120.0, 220.0, 5, "צבעית — צביעת דירות, תיקוני טיח וגימור.", true);
        seedPro("אחמד זועבי", "ahmad.carpentry@fixmate.com","0505555555", "carpentry", "חיפה",
                160.0, 280.0, 12, "נגר — רהיטים, ארונות ותיקוני עץ.", true);

        // ── בעלי מקצוע שממתינים לאישור — כדי שהאדמין יוכל לתרגל אישור/דחייה ──
        seedPro("נור חלבי",   "noor.pending@fixmate.com",   "0506666666", "electricity", "נצרת",
                140.0, 240.0, 3, "חשמלאי — בקשה חדשה להצטרפות לפלטפורמה.", false);
        seedPro("איתי גל",    "itay.pending@fixmate.com",   "0507777777", "renovation", "ראשון לציון",
                170.0, 320.0, 7, "איש שיפוצים — בקשה חדשה להצטרפות לפלטפורמה.", false);

        log.info("Seeding complete: 1 admin + {} approved professionals + pending requests.",
                proProfileRepository.findByApprovedTrue().size());
    }

    /** יוצר משתמש בעל מקצוע + פרופיל (מאושר, או ממתין לאישור לפי הדגל approved) */
    private void seedPro(String name, String email, String phone, String specialty,
                         String location, Double rateMin, Double rateMax, int years,
                         String bio, boolean approved) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Pro@123"));
        u.setRole(Role.PROFESSIONAL);
        u.setPhone(phone);
        userRepository.save(u);

        ProProfile p = new ProProfile();
        p.setUser(u);
        p.setSpecialty(specialty);
        p.setLocation(location);
        p.setHourlyRate(rateMin);
        p.setHourlyRateMax(rateMax);
        p.setYearsExperience(years);
        p.setBio(bio);
        p.setApproved(approved);   // מאושר → מופיע בחיפוש; ממתין → מגיע לאישור האדמין
        proProfileRepository.save(p);
    }
}
