package com.jvn.villagerretaliation.interaction.work;

import java.util.Map;

public record WorkResult(String status, Map<String, String> replacements, boolean progressed, boolean completed, boolean awardsSkillGrowth) {
    public static WorkResult idle(String status) {
        return idle(status, Map.of());
    }

    public static WorkResult idle(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, false, false, false);
    }

    public static WorkResult progressed(String status) {
        return progressed(status, Map.of());
    }

    public static WorkResult progressed(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, true, false, false);
    }

    public static WorkResult skilledProgress(String status) {
        return skilledProgress(status, Map.of());
    }

    public static WorkResult skilledProgress(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, true, false, true);
    }

    public static WorkResult completed(String status) {
        return completed(status, Map.of());
    }

    public static WorkResult completed(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, true, true, true);
    }
}
