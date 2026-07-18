package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.skill.VillagerSkillPractice;
import java.util.List;
import java.util.Map;

public record WorkResult(
        String status,
        Map<String, String> replacements,
        boolean progressed,
        boolean completed,
        List<VillagerSkillPractice> practice) {
    public WorkResult {
        replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        practice = practice == null ? List.of() : List.copyOf(practice);
    }

    public static WorkResult idle(String status) {
        return idle(status, Map.of());
    }

    public static WorkResult idle(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, false, false, List.of());
    }

    public static WorkResult progressed(String status) {
        return progressed(status, Map.of());
    }

    public static WorkResult progressed(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, true, false, List.of());
    }

    public static WorkResult progressedWithPractice(String status, List<VillagerSkillPractice> practice) {
        return progressedWithPractice(status, Map.of(), practice);
    }

    public static WorkResult progressedWithPractice(
            String status,
            Map<String, String> replacements,
            List<VillagerSkillPractice> practice) {
        return new WorkResult(status, replacements, true, false, practice);
    }

    public static WorkResult completed(String status) {
        return completed(status, Map.of());
    }

    public static WorkResult completed(String status, Map<String, String> replacements) {
        return new WorkResult(status, replacements, true, true, List.of());
    }

    public static WorkResult completedWithPractice(String status, List<VillagerSkillPractice> practice) {
        return completedWithPractice(status, Map.of(), practice);
    }

    public static WorkResult completedWithPractice(
            String status,
            Map<String, String> replacements,
            List<VillagerSkillPractice> practice) {
        return new WorkResult(status, replacements, true, true, practice);
    }
}
