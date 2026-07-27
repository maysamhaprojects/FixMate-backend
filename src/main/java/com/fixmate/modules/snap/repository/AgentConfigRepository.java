package com.fixmate.modules.snap.repository;

import com.fixmate.modules.snap.model.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * שליפת הגדרות הסוכן מהטבלה agent_config.
 * Spring Data JPA מייצר את המימוש אוטומטית.
 */
public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {

    /** שליפת הגדרה לפי מפתח, למשל "system_prompt" */
    Optional<AgentConfig> findByConfigKey(String configKey);
}
