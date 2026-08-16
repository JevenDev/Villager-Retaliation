package com.jvn.villagerretaliation.quest;

import java.util.LinkedHashSet;
import java.util.Set;

public record QuestTriggerEventDescriptor(
        String id,
        QuestDefinition.TriggerEvent event,
        Set<String> aliases,
        boolean continuous,
        long defaultCooldownTicks
) {
    public QuestTriggerEventDescriptor {
        id = QuestTriggerRegistry.normalizeEvent(id);
        event = event == null ? QuestDefinition.TriggerEvent.PLAYER_TICK : event;
        aliases = normalizeAliases(aliases);
        defaultCooldownTicks = Math.max(0L, defaultCooldownTicks);
    }

    private static Set<String> normalizeAliases(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String alias = QuestTriggerRegistry.normalizeEvent(value);
            if (!alias.isBlank()) {
                normalized.add(alias);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }
}
