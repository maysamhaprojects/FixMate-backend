package com.fixmate.common.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * שירות שליחת מיילים דרך SMTP (Gmail).
 * אם app.mail.enabled=false — לא נשלח מייל אמיתי (רק נרשם ללוג), כדי שהאפליקציה תעבוד גם בלי הגדרות.
 * השליחה עטופה ב-try/catch כדי שכישלון מייל לא יפיל את הפעולה (הרשמה/אישור/הזמנה).
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        if (!enabled || to == null || to.isBlank()) {
            System.out.println("[EMAIL skipped] to=" + to + " | subject=" + subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (from != null && !from.isBlank()) msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            System.out.println("[EMAIL sent] to=" + to + " | subject=" + subject);
        } catch (Exception e) {
            System.out.println("[EMAIL failed] to=" + to + " : " + e.getMessage());
        }
    }
}
