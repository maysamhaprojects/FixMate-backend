package com.fixmate.modules.snap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * שולח את תיאור התקלה ל-ChatGPT ומחזיר אבחון מובנה.
 * אם אין מפתח או שהקריאה נכשלה — מחזיר null, והבקר נופל חזרה
 * לזיהוי לפי מילות מפתח כך שהמסך ממשיך לעבוד.
 */
@Service
public class OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    /** מה ChatGPT מתבקש להחזיר — בדיוק המבנה שהפרונט כבר יודע לקרוא */
    private static final String SYSTEM_PROMPT = """
        You are a home-repair diagnostician for an Israeli handyman marketplace.
        The user describes a household problem in Hebrew or English.

        Reply with JSON only, no prose, with exactly these keys:
          "category"      — one of: plumbing, electricity, ac, painting, carpentry, appliance, lock, general
          "diagnosis"     — one or two sentences, in the SAME language the user wrote in
          "canDIY"        — true only if a non-professional can safely fix it; false for anything
                            involving mains electricity, gas, or structural work
          "estimatedCost" — a realistic range in Israeli shekels, formatted like "200-500 ILS"

        Be conservative with canDIY. Safety first.
        """;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.model:gpt-4o-mini}")
    private String model;

    /** האם הוגדר מפתח — הבקר בודק את זה לפני שהוא קורא לנו */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * מנתח תיאור טקסטואלי, תמונה, או שניהם.
     *
     * @param userText    תיאור התקלה — יכול להיות ריק אם יש תמונה
     * @param imageBase64 data URI מלא ("data:image/jpeg;base64,...") או null
     * @return מפת האבחון, או null אם לא ניתן לקבל תשובה תקינה מ-OpenAI
     */
    public Map<String, Object> analyze(String userText, String imageBase64) {
        if (!isConfigured()) return null;
        boolean hasImage = imageBase64 != null && imageBase64.startsWith("data:image");
        if ((userText == null || userText.isBlank()) && !hasImage) return null;

        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2,
                "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", buildUserContent(userText, hasImage ? imageBase64 : null))
                )
            );

            String raw = restClient.post()
                    .uri(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            // OpenAI עוטף את התשובה: choices[0].message.content מחזיק את ה-JSON שביקשנו
            JsonNode content = mapper.readTree(raw).path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                log.warn("OpenAI returned no content");
                return null;
            }

            JsonNode parsed = mapper.readTree(content.asText());
            String category = parsed.path("category").asText("general");
            String diagnosis = parsed.path("diagnosis").asText("");
            if (diagnosis.isBlank()) return null;   // תשובה חלקית — עדיף ליפול לגיבוי

            return Map.of(
                "category", category,
                "diagnosis", diagnosis,
                "canDIY", parsed.path("canDIY").asBoolean(false),
                "estimatedCost", parsed.path("estimatedCost").asText("100-300 ILS"),
                "source", "openai"
            );

        } catch (Exception e) {
            // מכסה מפתח שגוי, חריגת מכסה, נפילת רשת, JSON לא תקין
            log.warn("OpenAI call failed, falling back to keyword matching: {}", e.getMessage());
            return null;
        }
    }

    /**
     * בונה את גוף הודעת המשתמש. כשאין תמונה — מחרוזת פשוטה.
     * כשיש — מערך חלקים, שזה הפורמט ש-OpenAI מצפה לו עבור ניתוח תמונות.
     */
    private Object buildUserContent(String userText, String imageDataUri) {
        String text = (userText == null || userText.isBlank())
                ? "Diagnose the household problem shown in this photo."
                : userText;

        if (imageDataUri == null) return text;

        return List.of(
            Map.of("type", "text", "text", text),
            Map.of("type", "image_url", "image_url", Map.of("url", imageDataUri))
        );
    }
}
