package com.fixmate.modules.snap.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// זה השער של הצ׳אטבוט — מקבל תמונה וטקסט מהלקוח ומחזיר אבחון
// הפרונט שולח בקשה לכתובת הזו כשלקוח משתמש במסך Snap an Issue
@RestController
@RequestMapping("/api/snap")
public class SnapController {

    // מקבל תמונה וטקסט מהלקוח ומחזיר אבחון של התקלה
    // כרגע מחזיר תשובה קבועה — בעתיד נחבר AI אמיתי
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> request) {

        String text = request.getOrDefault("text", "");

        // 🚩 AI-FLAG: כאן בעתיד נחבר Vision AI אמיתי שיזהה את התקלה מהתמונה
        // לבינתיים מחזירים תשובה לפי מילות מפתח בטקסט

        String category = detectCategory(text);

        return ResponseEntity.ok(Map.of(
            "category", category,
            "diagnosis", getDiagnosis(category),
            "canDIY", getCanDIY(category),
            "estimatedCost", getEstimatedCost(category)
        ));
    }

    // זיהוי קטגוריה לפי מילות מפתח בטקסט
    private String detectCategory(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("water") || lower.contains("leak") || lower.contains("pipe") ||
            lower.contains("מים") || lower.contains("נזילה") || lower.contains("צנרת"))
            return "plumbing";
        if (lower.contains("electric") || lower.contains("socket") || lower.contains("wire") ||
            lower.contains("חשמל") || lower.contains("שקע") || lower.contains("קצר"))
            return "electricity";
        if (lower.contains("ac") || lower.contains("cool") ||
            lower.contains("מזגן") || lower.contains("קירור"))
            return "ac";
        if (lower.contains("crack") || lower.contains("wall") ||
            lower.contains("סדק") || lower.contains("קיר"))
            return "painting";
        return "general";
    }

    private String getDiagnosis(String category) {
        return switch (category) {
            case "plumbing" -> "Water leak detected — plumber needed";
            case "electricity" -> "Electrical issue detected — electrician needed";
            case "ac" -> "AC problem detected — technician needed";
            case "painting" -> "Wall damage detected — painter needed";
            default -> "General issue — professional assessment needed";
        };
    }

    private boolean getCanDIY(String category) {
        return category.equals("painting");
    }

    private String getEstimatedCost(String category) {
        return switch (category) {
            case "plumbing" -> "200-500 ILS";
            case "electricity" -> "150-400 ILS";
            case "ac" -> "200-600 ILS";
            case "painting" -> "20-50 ILS";
            default -> "100-300 ILS";
        };
    }
}