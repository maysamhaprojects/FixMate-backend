package com.fixmate.modules.auth.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.auth.repository.UserRepository;
import com.fixmate.security.jwt.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * פרטי המשתמש המחובר — שליפה ועדכון (עבור עמוד הפרופיל).
 * מוגן: כל בקשה דורשת התחברות (anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public UserController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /** מחזיר את פרטי המשתמש המחובר (בלי הסיסמה) */
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(toDto(user));
    }

    /** מעדכן שם, טלפון ואימייל של המשתמש המחובר. אם האימייל השתנה — מחזיר טוקן חדש. */
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@AuthenticationPrincipal User user,
                                      @RequestBody Map<String, String> body) {
        String fullName = body.get("fullName");
        String phone = body.get("phone");
        String email = body.get("email");

        if (fullName != null && !fullName.isBlank()) user.setFullName(fullName.trim());
        if (phone != null) user.setPhone(phone.trim());
        // תמונת פרופיל — "" מנקה, ערך חדש מעדכן
        if (body.containsKey("profilePicture")) {
            String pic = body.get("profilePicture");
            user.setProfilePicture(pic != null && !pic.isBlank() ? pic : null);
        }

        boolean emailChanged = false;
        if (email != null && !email.isBlank() && !email.trim().equalsIgnoreCase(user.getEmail())) {
            String newEmail = email.trim();
            if (userRepository.existsByEmail(newEmail)) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(newEmail);
            emailChanged = true;
        }

        User saved = userRepository.save(user);
        Map<String, Object> dto = toDto(saved);
        // אם האימייל השתנה — מנפיקים טוקן חדש כי הטוקן הישן קשור לאימייל הישן
        if (emailChanged) {
            dto.put("token", jwtService.generateToken(saved.getEmail()));
        }
        return ResponseEntity.ok(dto);
    }

    private Map<String, Object> toDto(User u) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", u.getId());
        dto.put("fullName", u.getFullName());
        dto.put("email", u.getEmail());
        dto.put("phone", u.getPhone());
        dto.put("role", u.getRole() != null ? u.getRole().name() : null);
        dto.put("profilePicture", u.getProfilePicture());
        return dto;
    }
}
