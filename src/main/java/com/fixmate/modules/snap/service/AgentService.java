package com.fixmate.modules.snap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixmate.modules.auth.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * הסוכן של FixMate.
 *
 * בניגוד ל-OpenAiService שמחזיר אבחון בודד, כאן מתנהלת שיחה:
 * המודל שואל שאלות הבהרה, מדריך לתיקון עצמי כשאפשר, ומזמין בעל מקצוע
 * כשצריך — דרך הכלים ב-AgentTools.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    /** תקרת בטיחות — מונעת לולאה אינסופית של קריאות לכלים */
    private static final int MAX_TOOL_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
        אתה העוזר החכם של FixMate. היום %s.

        הסדר הקבוע של כל שיחה — לעולם אל תדלג על שלב:
        1. קודם כול הבן את התקלה. אם המשתמש לא תיאר בעיה (למשל רק אמר "רוצה
           בעל מקצוע") — שאל קודם "מה התקלה?" ואל תשאל "באיזה תחום".
        2. אמור איזה בעל מקצוע מתאים לתקלה (אתה מזהה את התחום מהתיאור, לא הלקוח).
        3. אם התקלה פשוטה ובטוחה — הצע קודם הדרכה לתיקון עצמי.
        4. רק אם לא הסתדר, או שהתקלה לא בטוחה לתיקון עצמי — עבור להזמנת בעל מקצוע.

        חוקים:
        - ענה תמיד באותה שפה שבה המשתמש כתב. אם כתב בערבית — ענה בערבית.
          אם כתב בעברית — בעברית. לעולם אל תחליף שפה מיוזמתך.
        - גם אם המשתמש מבקש ישר בעל מקצוע — קודם הבן את התקלה ושקול הדרכה.
          אל תדלג להזמנה בלי להבין מה קרה.
        - אל תמציא בעלי מקצוע, מחירים או זמני הגעה.
        - השתמש רק במידע שהתקבל מהמערכת.
        - אל תפתח או תבטל הזמנה בלי אישור המשתמש.
        - מחיר סופי נקבע רק על ידי בעל מקצוע.
        - שאל בכל פעם שאלה אחת ברורה.

        בטיחות:
        - אם מדובר בריח גז, עשן, שריפה, חוטי חשמל חשופים, הצפה ליד חשמל,
          או אדם שנעול בפנים — עצור. הנחה להתרחק ולפנות למוקד חירום,
          ואל תפעיל שום כלי בתור הזה.
        - אל תדריך תיקון עצמי בחשמל, גז או עבודות מבנה. הפנה לבעל מקצוע מוסמך.

        הדרכה לתיקון עצמי — נסה תמיד קודם, כשזה בטוח:
        - אחרי שהבנת את התקלה, אם היא פשוטה ובטוחה לביצוע עצמי (למשל ברז מטפטף,
          סתימה קלה, ציר ארון רופף, החלפת נורה, איפוס מפסק) — הצע קודם הדרכה
          לתיקון עצמי, צעד אחר צעד, ושאל אם הצליח. אל תשאל על עיר או הזמנה בשלב זה.
        - אם המשתמש הצליח — מצוין, סיים. אם ניסה ולא הצליח, או שאמר שאינו רוצה
          לנסות לבד — רק אז עבור למהלך הזמנת בעל מקצוע.
        - לתקלות שאינן בטוחות לתיקון עצמי (חשמל, גז, מבנה) אל תציע הדרכה — עבור
          ישר למהלך ההזמנה.

        מהלך פתיחת בקשה (כשצריך בעל מקצוע) — לפי הסדר, שאלה אחת בכל שלב:
        1. אמור באיזה תחום מדובר.
        2. שאל שאלת אבחון אחת על הסימפטום.
           בחשמל שאל תמיד אם יש ריח שרוף, עשן או חוטים חשופים.
           במנעולן שאל תמיד אם יש אדם, ילד או בעל חיים נעול בפנים.
        3. שאל באיזה יישוב.
        4. שאל עד כמה זה דחוף, או לאיזה מועד.
        5. סכם את הבקשה במשפט אחד ושאל אם לחפש בעלי מקצוע זמינים.
        6. רק אחרי שהמשתמש הסכים — הפעל את כלי החיפוש.
        7. הצג את *כל* בעלי המקצוע שהכלי החזיר (שם, מחיר, דירוג) ותן ללקוח
           לבחור. אל תבחר לבד בעל מקצוע אחד ואל תדווח שמישהו "תפוס" בשלב הזה —
           הלקוח לא ביקש אף אחד ספציפי, הוא זה שבוחר.
        8. רק אחרי שהלקוח בחר בעל מקצוע ספציפי, ויש תאריך ושעה — הפעל
           check_availability על מי שהוא בחר.
           חובה: לעולם אל תאמר שבעל מקצוע "פנוי" או "זמין" בשעה כלשהי לפני
           שהפעלת check_availability וקיבלת available=true. אל תנחש זמינות
           ואל תמציא שעות פנויות — כל אמירה על זמינות חייבת לבוא מהכלי.
           - אם available=true: חזור על הפרטים ובקש אישור, ואז הזמן.
           - אם available=false: אמור שבעל המקצוע שבחר לא פנוי אז, הסבר לפי reason,
             והצע לו את השעות מהשדה sameProFreeSlotsSameDay (שעות פנויות אמיתיות
             של אותו בעל מקצוע באותו יום), או בעל מקצוע אחר מהרשימה שכבר הצגת.
             אל תמציא שעות — השתמש רק במה שהוחזר.
        9. רק אחרי אישור — הפעל את כלי ההזמנה.

        אם הלקוח שואל "מתי הוא פנוי?" או "אילו שעות יש?" — הפעל free_slots והצג
        את השעות שהוחזרו. אל תמציא שעות פנויות.

        בשינוי מועד המצב שונה: ללקוח כבר יש הזמנה עם בעל מקצוע ספציפי שהוא מכיר.
        לפני reschedule_booking הפעל check_availability על המועד החדש. אם אותו
        בעל מקצוע לא פנוי אז — כאן דווקא כן הצע לו בעל מקצוע חלופי (מהשדה
        alternatives), כי הוא כבר בתהליך עם בעל המקצוע המקורי.

        מהלך ביטול:
        - שלוף את ההזמנות, ואמור איזו הזמנה נמצאה ומתי.
        - אם אין הזמנה פעילה — אמור זאת, ואל תפעיל כלי.
        - אם ההזמנה כבר הושלמה — הסבר שלא ניתן לבטל, והצע פנייה לתמיכה.
        - אם הסיבה היא שהשעה לא מתאימה — הצע קודם שינוי מועד כחלופה לביטול.
        - אם המשתמש חוזר בו ואומר להשאיר — אשר שההזמנה נשארה פעילה, ואל תבטל.

        תאריכים ושעות:
        - לעולם אל תמציא שעה. "בבוקר", "בערב" או "בהקדם" אינם שעה — שאל
          לשעה מדויקת לפני שאתה מסכם ("באיזו שעה מחר בבוקר?").
        - "מחר" הוא היום שאחרי התאריך שצוין למעלה. אל תכתוב "היום" כשהמשתמש
          אמר "מחר".
        - אל תשתמש ב-00:00 כברירת מחדל. אם אין שעה — שאל.

        עבודה עם הכלים:
        - את שמות בעלי המקצוע, המחירים והדירוגים אתה מקבל אך ורק מכלי החיפוש.
          אסור לכתוב שם או דירוג שלא הופיע בתוצאה שקיבלת.
        - אל תקרא לאותו כלי חיפוש פעמיים באותה שיחה אם כבר קיבלת תוצאה.
        - כדי לחפש בעל מקצוע דרושים גם התחום וגם העיר. אם העיר לא נמסרה — שאל.
        - לפני פעולה שמשנה נתונים (הזמנה, ביטול, שינוי מועד, דירוג): חזור על
          הפרטים ובקש אישור, וסיים שם את התור. אם המשתמש כבר אישר בהודעה
          האחרונה — בצע מיד, בלי לשאול שוב.
        """;

    /**
     * דוגמאות לשיחות נכונות. נשלחות לפני השיחה האמיתית כדי להראות למודל את
     * הסגנון והקצב הרצויים — שאלה אחת בכל פעם, אישור לפני פעולה, וסירוב להמציא.
     * דוגמה עובדת טוב יותר מהוראה מילולית.
     */
    private static final List<Map<String, Object>> FEW_SHOT = List.of(
        // 1 — תקלה בטוחה: מציעים הדרכה לתיקון עצמי קודם, לא קופצים לעיר
        turn("user",      "יש לי נזילה מתחת לכיור במטבח"),
        turn("assistant", "נשמע כמו תקלת שרברבות. לרוב אפשר לתקן זאת לבד. נסה קודם: "
                        + "סגור את ברז הפינה מתחת לכיור ובדוק אם הנזילה נפסקה, "
                        + "וחזק את חיבורי הצנרת ביד. רוצה לנסות, או שאחפש לך שרברב?"),
        turn("user",      "הצלחתי, זה נפסק"),
        turn("assistant", "מעולה! שמח שהסתדר. אם משהו נוסף יצוץ, אני כאן."),

        // 2 — בטיחות: עוצרים, לא מזמינים
        turn("user",      "יש ריח גז חזק במטבח"),
        turn("assistant", "זה מצב מסוכן. צא מהבית עכשיו, אל תדליק או תכבה אור ואל תיגע "
                        + "במכשירי חשמל, ופנה מיד למוקד החירום של חברת הגז. "
                        + "כשהמקום יהיה בטוח נוכל לפתוח בקשת שירות."),

        // 3 — הדרכה לתיקון עצמי קודם; הזמנה רק אם לא הסתדר
        turn("user",      "הברז במטבח מטפטף כל הזמן"),
        turn("assistant", "לרוב זה אטם שחוק וקל להחליף לבד. נסה כך: סגור את ברז המים "
                        + "הראשי, פרק את ידית הברז, החלף את האטם (וושר) והרכב חזרה. "
                        + "רוצה לנסות, או שאחפש לך שרברב?"),
        turn("user",      "ניסיתי אבל זה עדיין מטפטף"),
        turn("assistant", "בסדר, אז עדיף בעל מקצוע. באיזו עיר נמצאת התקלה?")

        // הערה: אין כאן דוגמה שמציגה תוצאות חיפוש או הזמנה שבוצעה. דוגמה כזו
        // מלמדת את המודל להכריז על תוצאות בלי להפעיל כלי — כלומר להמציא.
        // את זרימת החיפוש וההזמנה מכתיבים ההוראות והכלים עצמם.
    );

    private static Map<String, Object> turn(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    /** כלים שמשנים נתונים — להבדיל מחיפוש/שליפה שרק קוראים */
    private static boolean isActionTool(String name) {
        return "create_booking".equals(name) || "cancel_booking".equals(name)
            || "reschedule_booking".equals(name) || "rate_professional".equals(name);
    }

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentTools tools;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.model:gpt-4o-mini}")
    private String model;

    public AgentService(AgentTools tools) {
        this.tools = tools;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param history שיחה קודמת מהפרונט — כל פריט {role, content}
     * @param user    המשתמש המחובר; כל פעולה תתבצע בשמו
     * @return {reply, actions} — actions מפרט אילו כלים הופעלו, לצורך הצגה בממשק
     */
    public Map<String, Object> chat(List<Map<String, Object>> history, String imageBase64, User user) {
        if (!isConfigured()) {
            return Map.of("reply", "", "error", "openai_not_configured");
        }

        // בונים את השיחה: הוראות, דוגמאות לשיחות נכונות, ואז ההיסטוריה האמיתית
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system",
                "content", String.format(SYSTEM_PROMPT, LocalDate.now())));

        messages.add(Map.of("role", "system", "content",
                "הדוגמאות הבאות ממחישות את הסגנון הנדרש. הן אינן חלק מהשיחה "
                + "הנוכחית — אל תתייחס אליהן כאל מידע על המשתמש."));
        messages.addAll(FEW_SHOT);
        messages.add(Map.of("role", "system", "content",
                "סוף הדוגמאות. השיחה האמיתית מתחילה כאן."));

        // מזריקים את כל בעלי המקצוע המאושרים עם ה-proId האמיתי שלהם. כך הסוכן
        // לא צריך לחפש מחדש בכל תור, ומחזיק תמיד את המזהה הדרוש להזמנה —
        // מה שמנע קודם את סגירת ההזמנה וגרם ללולאה.
        try {
            List<Map<String, Object>> pros = tools.allApprovedProsCompact();
            messages.add(Map.of("role", "system", "content",
                "בעלי המקצוע המאושרים במערכת (השתמש ב-proId המדויק מכאן כשמזמינים, "
                + "ואל תמציא מספרים אחרים):\n" + mapper.writeValueAsString(pros)
                + "\n\nהרשימה היא לשימושך הפנימי בלבד — כדי להחזיק proId להזמנה, "
                + "ולענות רק כשהמשתמש שואל במפורש מי הזול/המדורג ביותר. "
                + "אסור להציג שמות בעלי מקצוע לפני שאספת גם עיר וגם מועד (תאריך "
                + "ושעה). כשהמשתמש מבקש בעל מקצוע — אל תציג שמות מהרשימה; במקום "
                + "זאת שאל תחילה על העיר, אחר כך על המועד, ורק אז הצג בעל מקצוע "
                + "מתאים אחד ובדוק את זמינותו. אל תציג את הרשימה מיוזמתך, ואל "
                + "תפנה אליה בתקלה חדשה, בביטול, בשינוי מועד או בדירוג."));
        } catch (Exception ignored) { /* אם נכשל, המודל עדיין יכול לקרוא ל-search */ }

        messages.addAll(history);

        // תמונה מצורפת — נדחפת להודעה האחרונה של המשתמש
        boolean hasImage = imageBase64 != null && imageBase64.startsWith("data:image");
        if (hasImage && !messages.isEmpty()) {
            Map<String, Object> last = messages.get(messages.size() - 1);
            if ("user".equals(last.get("role"))) {
                messages.set(messages.size() - 1, Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", String.valueOf(last.getOrDefault("content", ""))),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageBase64))
                    )
                ));
            }
            // כשיש תמונה — מורים לסוכן לנתח אותה, לא לשאול "מה התקלה"
            messages.add(Map.of("role", "system", "content",
                "המשתמש צירף תמונה. הסתכל עליה ותאר בקצרה את התקלה שאתה רואה בה, "
                + "וזהה את התחום המתאים. אל תשאל 'מה התקלה' — אתה רואה אותה בתמונה. "
                + "אם התמונה לא ברורה מספיק, שאל שאלה ממוקדת אחת. אחר כך המשך "
                + "כרגיל: הדרכה לתיקון עצמי אם בטוח, או הזמנת בעל מקצוע."));
        }

        List<String> actions = new ArrayList<>();

        // אכיפה בקוד: אם ההודעה הקודמת שלנו הייתה שאלת אישור והמשתמש ענה בחיוב,
        // אסור למודל לשאול שוב או להסתפק בהצגת תוצאות חיפוש. ההיסטוריה מהפרונט
        // היא טקסט בלבד, כך שה-proId שהתקבל בחיפוש קודם אבד — לכן הסוכן צריך
        // קודם לחפש כדי לשחזר אותו, ורק אז להזמין. ממשיכים לכפות כלי עד
        // שפעולה אמיתית (הזמנה/ביטול/שינוי/דירוג) הצליחה.
        boolean mustAct = userJustConfirmed(history);
        String intendedAction = mustAct ? intendedAction(history) : null;
        String prereqTool = mustAct ? prerequisiteTool(intendedAction) : null;
        boolean prereqDone = false;
        boolean actionDone = false;

        if (mustAct) {
            messages.add(Map.of("role", "system", "content",
                "The user just confirmed. Carry out the action now using the tools. "
                + "Do not ask anything and do not just re-present search results — finish it."));
        }

        // כוונת ניהול הזמנה (ביטול / סטטוס / שינוי מועד / דירוג): מכריחים
        // get_my_orders בסבב הראשון כדי שהסוכן יעבוד מול ההזמנה האמיתית ולא
        // יטעה לחשוב שמדובר בתקלה חדשה.
        String orderIntentForce = (!mustAct && wantsOrderManagement(history)) ? "get_my_orders" : null;
        if (orderIntentForce != null) {
            messages.add(Map.of("role", "system", "content",
                "המשתמש מדבר על הזמנה קיימת. שלוף עם get_my_orders. לביטול או שינוי "
                + "מועד הצע אך ורק הזמנות מ-activeOrders — לעולם אל תציע לבטל הזמנה "
                + "שכבר הושלמה, בוטלה או שעבר זמנה. אם אין הזמנות פעילות, אמור זאת."));
        }

        // תקלה בטוחה בהודעה הראשונה: מפיקים הדרכה לתיקון עצמי בקריאה נפרדת
        // וממוקדת, כדי שמהלך ההזמנה שבפרומפט הראשי לא יתחרה ולא יגרום לקפיצה
        // לשאלת עיר. זו התנהגות שנאכפת בקוד, לא בבקשה למודל.
        boolean forceDiy = !mustAct && orderIntentForce == null && shouldForceDiy(history);
        if (forceDiy) {
            String userText = String.valueOf(
                history.get(history.size() - 1).getOrDefault("content", ""));
            try {
                String guidance = diyGuidance(userText);
                if (guidance != null && !guidance.isBlank()) {
                    return Map.of("reply", guidance, "actions", List.of());
                }
            } catch (Exception e) {
                log.warn("DIY guidance failed, falling through to normal flow: {}", e.getMessage());
            }
        }

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                // אחרי אישור: קודם כופים את כלי-העזר (חיפוש/שליפה) כדי לשחזר
                // את המזהה שאבד, ואז כופים את פעולת היעד עצמה — בשמה המדויק,
                // כך שהמודל לא יכול להסתפק בחיפוש חוזר.
                String forceToolName = null;
                if (mustAct && !actionDone) {
                    // אם צריך כלי-עזר ועוד לא רץ — כופים אותו; אחרת כופים את
                    // פעולת היעד ישירות (להזמנה אין כלי-עזר, ה-proId כבר בהקשר)
                    forceToolName = (prereqTool != null && !prereqDone) ? prereqTool : intendedAction;
                } else if (orderIntentForce != null && round == 0) {
                    // בפנייה ראשונה על ניהול הזמנה — שולפים קודם את ההזמנות
                    forceToolName = orderIntentForce;
                }

                // בתור של הדרכה עצמית — קוראים למודל בלי כלים בכלל, כך שהוא
                // מוכרח להשיב טקסט (הדרכה) ולא יכול לחפש או להזמין.
                JsonNode message = forceDiy
                        ? callModelNoTools(messages)
                        : callModel(messages, forceToolName);
                if (message == null) break;

                JsonNode toolCalls = message.path("tool_calls");

                // אין קריאה לכלי — זו התשובה הסופית למשתמש
                if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                    return Map.of(
                        "reply", message.path("content").asText(""),
                        "actions", actions
                    );
                }

                // המודל ביקש להפעיל כלים — מריצים ומחזירים לו את התוצאות
                messages.add(mapper.convertValue(message, Map.class));

                for (JsonNode call : toolCalls) {
                    String name = call.path("function").path("name").asText();
                    String rawArgs = call.path("function").path("arguments").asText("{}");

                    Map<String, Object> args;
                    try {
                        args = mapper.readValue(rawArgs, Map.class);
                    } catch (Exception e) {
                        args = Map.of();
                    }

                    Object result = tools.run(name, args, user);

                    // רק פעולות שהצליחו נרשמות — אחרת הממשק מכריז על הצלחה
                    // שלא קרתה
                    boolean failed = result instanceof Map<?, ?> m && m.containsKey("error");
                    if (!failed) actions.add(name);

                    // מסמנים התקדמות בשרשרת הכפייה: קודם כלי-העזר, אחר כך הפעולה
                    if (name.equals(prereqTool)) prereqDone = true;
                    if (!failed && isActionTool(name)) actionDone = true;

                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", call.path("id").asText());
                    toolMsg.put("content", mapper.writeValueAsString(result));
                    messages.add(toolMsg);
                }
            }

            log.warn("Agent hit the tool-round limit without producing a reply");
            return Map.of("reply", "", "error", "too_many_steps", "actions", actions);

        } catch (Exception e) {
            log.warn("Agent call failed: {}", e.getMessage());
            return Map.of("reply", "", "error", "openai_failed");
        }
    }

    /**
     * תקלות בטוחות ופשוטות שלגביהן מציעים תמיד הדרכה לתיקון עצמי לפני הזמנה.
     * חשמל/גז/מבנה לא כאן בכוונה — שם עוברים ישר לבעל מקצוע.
     */
    private static final List<String> DIY_HINTS = List.of(
        "מטפטף", "טפטוף", "נזיל", "ברז", "סתימה", "סתום", "נסתם", "פקק",
        "ציר", "רופף", "מתנדנד", "נורה", "מפסק ירד", "לא נסגר", "לא נפתח",
        // ערבית
        "تسريب", "يقطر", "حنفية", "صنبور", "انسداد", "مسدود", "مفصلة"
    );

    /**
     * ביטויים שמסמנים שהמשתמש כבר ניסה ולא הצליח, או שאינו רוצה לנסות לבד —
     * אז מדלגים על ההדרכה. בקשה לבעל מקצוע לבדה אינה כאן: לפי הזרימה, גם אם
     * המשתמש מבקש בעל מקצוע, מציעים לו הדרכה קודם אם התקלה פשוטה.
     */
    private static final List<String> DIY_FAILED = List.of(
        "לא הצלחתי", "לא עבד", "עדיין", "לא הסתדר", "ניסיתי", "לא עזר",
        "לא רוצה לנסות", "לא רוצה לתקן", "בלי הדרכה"
    );

    /**
     * האם להכריח הדרכה לתיקון עצמי: כשההודעה הראשונה מתארת תקלה בטוחה,
     * המשתמש עוד לא ניסה לתקן, ואין רמז לחירום. מונע מהמודל לקפוץ לחיפוש.
     */
    private boolean shouldForceDiy(List<Map<String, Object>> history) {
        // רק על ההודעה הראשונה של המשתמש בשיחה
        long userMsgs = history.stream().filter(m -> "user".equals(m.get("role"))).count();
        if (userMsgs != 1) return false;

        String allText = history.stream()
                .map(m -> String.valueOf(m.getOrDefault("content", "")))
                .reduce("", (a, b) -> a + " " + b).toLowerCase();

        boolean isDiyable = DIY_HINTS.stream().anyMatch(allText::contains);
        boolean alreadyTried = DIY_FAILED.stream().anyMatch(allText::contains);
        return isDiyable && !alreadyTried;
    }

    /** ביטויים שמעידים שהמשתמש מדבר על הזמנה קיימת, לא על תקלה חדשה */
    private static final List<String> ORDER_INTENT = List.of(
        "לבטל", "ביטול", "בטל", "לשנות", "מה קורה עם ההזמנה", "איפה בעל המקצוע",
        "ההזמנה שלי", "ההזמנות שלי", "הסטטוס", "הזמנה שלי",
        // דירוג אחרי סיום עבודה
        "לדרג", "דירוג", "לתת דירוג", "העבודה הסתיימה", "העבודה נגמרה", "סיים את העבודה",
        "cancel", "my order", "my booking", "reschedule", "status", "rate", "review",
        // ערבית
        "الغاء", "إلغاء", "ألغي", "طلبي", "حجزي", "تقييم", "تغيير الموعد"
    );

    /** האם ההודעה האחרונה של המשתמש עוסקת בהזמנה קיימת */
    private boolean wantsOrderManagement(List<Map<String, Object>> history) {
        if (history.isEmpty()) return false;
        Map<String, Object> last = history.get(history.size() - 1);
        if (!"user".equals(last.get("role"))) return false;
        String text = String.valueOf(last.getOrDefault("content", "")).toLowerCase();
        return ORDER_INTENT.stream().anyMatch(text::contains);
    }

    /** מילות אישור בעברית, אנגלית וערבית */
    private static final List<String> AFFIRMATIVES = List.of(
        "כן", "מאשר", "מאשרת", "אישור", "בטח", "בסדר", "אוקיי", "אוקי", "סבבה",
        "yes", "yep", "yeah", "ok", "okay", "sure", "confirm", "go ahead",
        "نعم", "أكيد", "موافق"
    );

    /**
     * ניסוחים שמעידים שהשאלה שלנו הייתה אישור לביצוע פעולה — להבדיל משאלת
     * בחירה או המשך ("תרצה לבחור בו?"), שאחריה אין לכפות קריאה לכלי.
     */
    private static final List<String> ACTION_PROMPTS = List.of(
        "לאשר", "לבטל", "להזמין", "לשנות", "לדרג", "לשמור את הדירוג", "לשלוח את הבקשה",
        "confirm", "shall i book", "cancel", "reschedule", "rate"
    );

    /**
     * האם ההודעה האחרונה של המשתמש היא אישור לביצוע פעולה שהצענו.
     * דורש שההודעה שלנו הייתה שאלת אישור לפעולה ממש — אחרת "כן" סתם
     * (למשל בבחירת בעל מקצוע) היה גורם לכפיית כלי ולולאה.
     */
    private boolean userJustConfirmed(List<Map<String, Object>> history) {
        if (history.size() < 2) return false;

        Map<String, Object> last = history.get(history.size() - 1);
        Map<String, Object> prev = history.get(history.size() - 2);
        if (!"user".equals(last.get("role")) || !"assistant".equals(prev.get("role"))) return false;

        String assistantText = String.valueOf(prev.getOrDefault("content", ""));
        if (!assistantText.contains("?") && !assistantText.contains("؟")) return false;

        String lowered = assistantText.toLowerCase();
        boolean isActionPrompt = ACTION_PROMPTS.stream().anyMatch(lowered::contains);
        if (!isActionPrompt) return false;

        // אישור הזמנה נחשב רק אם כבר הוצג בעל מקצוע קונקרטי (מופיע מחיר בשיחה).
        // כך "כן" לשאלת הבהרה כמו "רק כדי לאשר, המועד 10:00?" — שנשאלה לפני
        // שחיפשנו בעל מקצוע — לא ייחשב בטעות כאישור הזמנה ולא ינסה להזמין.
        String action = intendedAction(history);
        if ("create_booking".equals(action) && !proAlreadyPresented(history)) return false;

        String reply = String.valueOf(last.getOrDefault("content", ""))
                .toLowerCase().replaceAll("[.!,\\s]+$", "").trim();
        if (reply.length() > 20) return false;   // תשובה ארוכה — כנראה לא אישור פשוט

        return AFFIRMATIVES.stream().anyMatch(a -> reply.equals(a) || reply.startsWith(a + " "));
    }

    /** האם כבר הוצג בעל מקצוע בשיחה — מזוהה לפי אזכור מחיר בהודעת הסוכן */
    private boolean proAlreadyPresented(List<Map<String, Object>> history) {
        for (Map<String, Object> m : history) {
            if (!"assistant".equals(m.get("role"))) continue;
            String t = String.valueOf(m.getOrDefault("content", ""));
            if (t.contains("ש\"ח") || t.contains("ILS") || t.contains("לשעה")
                    || t.contains("دولار") || t.contains("شيكل")) return true;
        }
        return false;
    }

    /**
     * לפי שאלת האישור שהצגנו, איזו פעולה המשתמש אישר. מחזיר את שם הכלי
     * המבצע — כדי שנוכל לכפות אותו ספציפית ולא להסתפק ב"איזשהו כלי".
     */
    private String intendedAction(List<Map<String, Object>> history) {
        if (history.size() < 2) return null;
        String q = String.valueOf(history.get(history.size() - 2).getOrDefault("content", "")).toLowerCase();
        if (q.contains("לבטל") || q.contains("ביטול") || q.contains("cancel")) return "cancel_booking";
        // שינוי מועד דורש פועל שינוי מפורש. "מועד" לבדו אומר תאריך/שעה ומופיע
        // גם בהזמנה חדשה, ולכן אינו כאן — אחרת הזמנה חדשה תסווג בטעות כשינוי.
        if (q.contains("לשנות") || q.contains("להזיז") || q.contains("reschedule")) return "reschedule_booking";
        if (q.contains("דירוג") || q.contains("לדרג") || q.contains("rate")) return "rate_professional";
        return "create_booking";
    }

    /**
     * הכלי שצריך לרוץ קודם כדי לשחזר מזהה שאבד מההיסטוריה הטקסטואלית.
     * להזמנה אין צורך — רשימת בעלי המקצוע (עם proId) מוזרקת בכל תור.
     * לביטול/שינוי/דירוג עדיין צריך את bookingId מ-get_my_orders.
     */
    private String prerequisiteTool(String action) {
        return "create_booking".equals(action) ? null : "get_my_orders";
    }

    /**
     * קריאה אחת ל-OpenAI.
     * forceToolName: אם לא null — כופה קריאה לכלי הזה בדיוק (tool_choice ממוקד);
     *                אם "" (מחרוזת ריקה) — כופה כלי כלשהו; אם null — המודל חופשי.
     */
    /**
     * הדרכה לתיקון עצמי — קריאה נפרדת עם פרומפט מינימלי, כדי שמהלך ההזמנה
     * שבפרומפט הראשי לא יתחרה. מחזיר טקסט הדרכה בשפת המשתמש.
     */
    private String diyGuidance(String userText) throws Exception {
        List<Map<String, Object>> msgs = List.of(
            Map.of("role", "system", "content",
                "אתה טכנאי תיקוני בית. המשתמש תיאר תקלה פשוטה ובטוחה (למשל ברז "
                + "מטפטף, סתימה קלה, ציר רופף). תמיד החזר 2-4 צעדים קצרים וברורים "
                + "לתיקון עצמי, גם אם המשתמש ביקש בעל מקצוע — קודם הצע לו לנסות "
                + "לבד. סיים במשפט: 'רוצה לנסות, או שאחפש לך בעל מקצוע?'. "
                + "ענה באותה שפה שבה המשתמש כתב. אל תשאל על עיר, מועד או פרטים. "
                + "החריג היחיד: אם התקלה כרוכה בחשמל, גז או עבודות מבנה — אל תיתן "
                + "צעדים, אמור שצריך בעל מקצוע מוסמך ושאל אם לחפש."),
            Map.of("role", "user", "content", userText)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("messages", msgs);

        String raw = restClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        return mapper.readTree(raw).path("choices").path(0).path("message").path("content").asText("");
    }

    /** קריאה ללא כלים כלל — המודל יכול רק להשיב טקסט (להדרכה עצמית) */
    private JsonNode callModelNoTools(List<Map<String, Object>> messages) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("messages", messages);

        String raw = restClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode message = mapper.readTree(raw).path("choices").path(0).path("message");
        return message.isMissingNode() ? null : message;
    }

    private JsonNode callModel(List<Map<String, Object>> messages, String forceToolName) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("messages", messages);
        body.put("tools", tools.definitions());
        if (forceToolName != null && !forceToolName.isBlank()) {
            body.put("tool_choice", Map.of("type", "function",
                    "function", Map.of("name", forceToolName)));
        } else if (forceToolName != null) {
            body.put("tool_choice", "required");
        }

        String raw = restClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode message = mapper.readTree(raw).path("choices").path(0).path("message");
        return message.isMissingNode() ? null : message;
    }
}
