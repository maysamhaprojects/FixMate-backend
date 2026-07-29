package com.fixmate.config;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.availability.model.ProAvailability;
import com.fixmate.modules.availability.repository.ProAvailabilityRepository;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.repository.ProProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * מוודא שלכל בעל מקצוע יש שעות עבודה מוגדרות.
 *
 * למה: בעל מקצוע בלי זמינות מוגדרת מופיע כ"שעות לא ידועות" והסוכן אינו יכול
 * להציע לו מועדים קונקרטיים. הזורע הזה משלים ברירת מחדל סבירה —
 * ראשון–חמישי, 08:00–18:00 — לכל בעל מקצוע שאין לו עדיין לוח.
 *
 * רץ בכל עלייה, אך הוא אידמפוטנטי: מי שכבר הגדיר שעות (למשל דרך הדשבורד)
 * לא נוגעים בו — כך הגדרות אמיתיות נשמרות.
 */
@Component
@Order(2)   // אחרי DataSeeder — כדי שבעלי המקצוע כבר קיימים
public class AvailabilitySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AvailabilitySeeder.class);

    /** ימי העבודה השבועיים (שמות באנגלית, כמו DayOfWeek.name()) */
    private static final List<String> WORK_DAYS =
            List.of("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY");
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(18, 0);

    private final ProProfileRepository proProfileRepository;
    private final ProAvailabilityRepository availabilityRepository;

    public AvailabilitySeeder(ProProfileRepository proProfileRepository,
                              ProAvailabilityRepository availabilityRepository) {
        this.proProfileRepository = proProfileRepository;
        this.availabilityRepository = availabilityRepository;
    }

    @Override
    public void run(String... args) {
        int seeded = 0;
        for (ProProfile profile : proProfileRepository.findAll()) {
            User pro = profile.getUser();
            if (pro == null) continue;

            // כבר יש לו שעות — לא נוגעים (שומר על הגדרות אמיתיות של בעל המקצוע)
            if (!availabilityRepository.findByProId(pro.getId()).isEmpty()) continue;

            for (String day : WORK_DAYS) {
                ProAvailability slot = new ProAvailability();
                slot.setPro(pro);
                slot.setDayOfWeek(day);
                slot.setStartTime(START);
                slot.setEndTime(END);
                slot.setAvailable(true);
                availabilityRepository.save(slot);
            }
            seeded++;
        }

        if (seeded > 0) {
            log.info("Seeded default availability (Sun-Thu 08:00-18:00) for {} professional(s).", seeded);
        }
    }
}
