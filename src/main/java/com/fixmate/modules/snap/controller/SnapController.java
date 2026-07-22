package com.fixmate.modules.snap.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.snap.service.AgentService;
import com.fixmate.modules.snap.service.OpenAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// זה השער של הצ׳אטבוט — מקבל תמונה וטקסט מהלקוח ומחזיר אבחון
// הפרונט שולח בקשה לכתובת הזו כשלקוח משתמש במסך Snap an Issue
@RestController
@RequestMapping("/api/snap")
public class SnapController {

    private final OpenAiService openAi;
    private final AgentService agent;

    public SnapController(OpenAiService openAi, AgentService agent) {
        this.openAi = openAi;
        this.agent = agent;
    }

    /**
     * שיחה עם הסוכן — הוא מאפיין את התקלה, מדריך לתיקון עצמי כשאפשר,
     * ומזמין בעל מקצוע לאחר אישור מפורש של הלקוח.
     *
     * body: { messages: [{role, content}, ...], imageBase64?: string }
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request,
                                                    @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "not_authenticated"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) request.getOrDefault("messages", List.of());

        if (messages.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "messages_required"));
        }

        String image = (String) request.get("imageBase64");
        return ResponseEntity.ok(agent.chat(messages, image, user));
    }

    // מקבל טקסט מהלקוח ומחזיר אבחון של התקלה.
    // מנסה קודם ChatGPT; אם אין מפתח או שהקריאה נכשלה — נופל לזיהוי לפי מילות
    // מפתח, כך שהמסך ממשיך לעבוד גם בלי חיבור ל-AI.
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> request) {

        String text = request.getOrDefault("text", "");
        String image = request.get("imageBase64");

        Map<String, Object> ai = openAi.analyze(text, image);
        if (ai != null) return ResponseEntity.ok(ai);

        // גיבוי — זיהוי לפי מילות מפתח
        String category = detectCategory(text);

        return ResponseEntity.ok(Map.of(
            "category", category,
            "diagnosis", getDiagnosis(category),
            "canDIY", getCanDIY(category),
            "estimatedCost", getEstimatedCost(category),
            "source", "keywords"
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