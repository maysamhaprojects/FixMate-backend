package com.fixmate.modules.auth.service;

import com.fixmate.modules.auth.dto.AuthResponse;
import com.fixmate.modules.auth.dto.LoginRequest;
import com.fixmate.modules.auth.dto.RegisterRequest;
import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.repository.ProProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.fixmate.security.jwt.JwtService;
import com.fixmate.common.email.EmailService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProProfileRepository proProfileRepository;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       ProProfileRepository proProfileRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.proProfileRepository = proProfileRepository;
        this.emailService = emailService;
    }

    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }
        User user = new User();
        user.setFullName(req.getFullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(req.getRole());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        userRepository.save(user);

        // אם נרשם כבעל מקצוע — צור פרופיל עם הפרטים שנשלחו (מקצוע, עיר, מחיר, תיאור)
        if (req.getRole().name().equals("PROFESSIONAL")) {
            ProProfile profile = new ProProfile();
            profile.setUser(user);
            profile.setApproved(false);
            if (req.getSpecialty() != null) profile.setSpecialty(req.getSpecialty());
            if (req.getLocation() != null) profile.setLocation(req.getLocation());
            if (req.getHourlyRate() != null) profile.setHourlyRate(req.getHourlyRate());
            if (req.getHourlyRateMax() != null) profile.setHourlyRateMax(req.getHourlyRateMax());
            if (req.getBio() != null) profile.setBio(req.getBio());
            if (req.getYearsExperience() != null) profile.setYearsExperience(req.getYearsExperience());
            if (req.getDocuments() != null) profile.setDocuments(req.getDocuments());
            proProfileRepository.save(profile);

            // מייל לבעל המקצוע — הבקשה התקבלה וממתינה לאישור
            emailService.send(user.getEmail(),
                "FixMate — בקשתך התקבלה",
                "שלום " + user.getFullName() + ",\n\n" +
                "בקשתך להצטרף כבעל מקצוע ב-FixMate התקבלה וממתינה לאישור מנהל.\n" +
                "נעדכן אותך במייל ברגע שהבקשה תיבדק.\n\n" +
                "תודה,\nצוות FixMate");
        } else {
            // מייל ברוכים הבאים ללקוח
            emailService.send(user.getEmail(),
                "ברוך הבא ל-FixMate!",
                "שלום " + user.getFullName() + ",\n\n" +
                "החשבון שלך נוצר בהצלחה. אפשר להתחיל להזמין בעלי מקצוע.\n\n" +
                "צוות FixMate");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getRole().name(), user.getFullName());
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }
        if (user.isSuspended()) {
            throw new RuntimeException("Your account has been suspended");
        }
        if (user.getRole().name().equals("PROFESSIONAL")) {
            ProProfile profile = proProfileRepository.findByUserId(user.getId()).orElse(null);
            if (profile != null && profile.isRejected()) {
                String reason = profile.getRejectionReason();
                throw new RuntimeException("Your application was rejected"
                        + (reason != null && !reason.isBlank() ? ": " + reason : ""));
            }
            boolean isApproved = profile != null && profile.isApproved();
            if (!isApproved) {
                throw new RuntimeException("Your account is pending admin approval");
            }
        }
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getRole().name(), user.getFullName());
    }
}