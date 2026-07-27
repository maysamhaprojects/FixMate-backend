package com.fixmate.config;

import com.fixmate.modules.auth.model.Role;
import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * זריעת חשבון אדמין ברירת-מחדל — רצה רק כשמסד הנתונים ריק לגמרי (אפס משתמשים).
 *
 * המטרה: פרויקט שמורידים מ-GitHub ומריצים על DB חדש יאפשר כניסת אדמין מיד,
 * בלי ליצור משתמש ידנית (אי אפשר להירשם כאדמין דרך האתר).
 * חשבונות לקוח ובעל מקצוע — נרשמים דרך האתר כרגיל.
 *
 * חשוב: כשה-DB כבר מכיל נתונים (למשל אצל המפַתחת) — ה-seeder לא עושה כלום.
 *
 * ⚠️ פרטי ברירת מחדל לפיתוח/הדגמה בלבד — יש לשנותם בסביבת פרודקשן.
 *   אדמין: admin@fixmate.com / Admin@123
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // רצים רק על DB ריק — כדי לא לגעת בנתונים קיימים
        if (userRepository.count() > 0) {
            return;
        }

        User admin = new User();
        admin.setFullName("FixMate Admin");
        admin.setEmail("admin@fixmate.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin@123")); // מוצפן BCrypt
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.info("Empty database detected — seeded default admin: admin@fixmate.com");
    }
}
