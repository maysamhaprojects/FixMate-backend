package com.fixmate.modules.snap.model;

import jakarta.persistence.*;

/**
 * הגדרות הסוכן הנשמרות במסד הנתונים (טבלת agent_config), כזוגות מפתח–ערך.
 * כך אפשר לערוך את ה-system prompt של הסוכן ישירות ב-DB, בלי לקמפל מחדש את הקוד.
 */
@Entity
@Table(name = "agent_config")
public class AgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** מפתח ההגדרה, למשל "system_prompt" */
    @Column(nullable = false, unique = true)
    private String configKey;

    /** הערך — טקסט ארוך (הפרומפט עצמו) */
    @Column(columnDefinition = "LONGTEXT")
    private String configValue;

    public AgentConfig() {}

    public AgentConfig(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
    }

    public Long getId() { return id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
}
