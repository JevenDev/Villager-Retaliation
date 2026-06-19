package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestTriggerRegistry {
    private static final long CONTINUOUS_TRIGGER_COOLDOWN_TICKS = 20L * 30L;
    private static final List<QuestTriggerEventDescriptor> REGISTRATIONS = List.of(
            register(
                    "player_tick",
                    QuestDefinition.TriggerEvent.PLAYER_TICK,
                    aliases("tick", "player"),
                    true,
                    CONTINUOUS_TRIGGER_COOLDOWN_TICKS),
            register(
                    "proximity",
                    QuestDefinition.TriggerEvent.PROXIMITY,
                    aliases("nearby", "near_provider"),
                    true,
                    CONTINUOUS_TRIGGER_COOLDOWN_TICKS),
            register(
                    "started",
                    QuestDefinition.TriggerEvent.STARTED,
                    aliases("start", "quest_started"),
                    false,
                    0L),
            register(
                    "progress",
                    QuestDefinition.TriggerEvent.PROGRESS,
                    aliases("quest_progress", "updated"),
                    false,
                    0L),
            register(
                    "stage_changed",
                    QuestDefinition.TriggerEvent.STAGE_CHANGED,
                    aliases("stage", "stage_entered", "stage_set"),
                    false,
                    0L),
            register(
                    "completed",
                    QuestDefinition.TriggerEvent.COMPLETED,
                    aliases("complete", "quest_completed"),
                    false,
                    0L),
            register(
                    "abandoned",
                    QuestDefinition.TriggerEvent.ABANDONED,
                    aliases("abandon", "quest_abandoned"),
                    false,
                    0L),
            register(
                    "expired",
                    QuestDefinition.TriggerEvent.EXPIRED,
                    aliases("expire", "quest_expired"),
                    false,
                    0L)
    );
    private static final Map<String, QuestTriggerEventDescriptor> BY_ALIAS = descriptorsByAlias();
    private static final Map<QuestDefinition.TriggerEvent, QuestTriggerEventDescriptor> BY_EVENT = descriptorsByEvent();

    private QuestTriggerRegistry() {
    }

    public static List<QuestTriggerEventDescriptor> descriptors() {
        return REGISTRATIONS;
    }

    public static QuestDefinition.TriggerEvent eventBySerializedName(String value) {
        QuestTriggerEventDescriptor descriptor = BY_ALIAS.get(normalizeEvent(value));
        return descriptor == null ? null : descriptor.event();
    }

    public static String canonicalEventId(String value) {
        String normalized = normalizeEvent(value);
        QuestTriggerEventDescriptor descriptor = BY_ALIAS.get(normalized);
        return descriptor == null ? normalized : descriptor.id();
    }

    public static String canonicalEventId(QuestDefinition.TriggerEvent event) {
        QuestTriggerEventDescriptor descriptor = BY_EVENT.get(event);
        return descriptor == null ? "" : descriptor.id();
    }

    public static long defaultCooldownTicks(QuestDefinition.TriggerEvent event) {
        QuestTriggerEventDescriptor descriptor = BY_EVENT.get(event);
        return descriptor == null ? 0L : descriptor.defaultCooldownTicks();
    }

    public static boolean isContinuous(QuestDefinition.TriggerEvent event) {
        QuestTriggerEventDescriptor descriptor = BY_EVENT.get(event);
        return descriptor != null && descriptor.continuous();
    }

    public static QuestTriggerIndex index(List<CompiledQuestTrigger> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return QuestTriggerIndex.EMPTY;
        }
        List<CompiledQuestTrigger> ordered = new ArrayList<>();
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> byEvent =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        Map<QuestDefinition.TriggerEvent, List<CompiledQuestTrigger>> globalByEvent =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        Map<QuestDefinition.TriggerEvent, Map<String, List<CompiledQuestTrigger>>> byEventAndStage =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        List<CompiledQuestTrigger> continuous = new ArrayList<>();

        for (CompiledQuestTrigger compiledTrigger : triggers) {
            if (compiledTrigger == null || compiledTrigger.definition() == null) {
                continue;
            }
            QuestDefinition.Trigger trigger = compiledTrigger.definition();
            QuestDefinition.TriggerEvent event = trigger.event();
            ordered.add(compiledTrigger);
            byEvent.computeIfAbsent(event, ignored -> new ArrayList<>()).add(compiledTrigger);
            if (isContinuous(event)) {
                continuous.add(compiledTrigger);
            }
            if (trigger.stages().isEmpty()) {
                globalByEvent.computeIfAbsent(event, ignored -> new ArrayList<>()).add(compiledTrigger);
                continue;
            }
            Map<String, List<CompiledQuestTrigger>> stages =
                    byEventAndStage.computeIfAbsent(event, ignored -> new LinkedHashMap<>());
            for (String stage : trigger.stages()) {
                String normalized = QuestTriggerIndex.normalizeStage(stage);
                if (!normalized.isBlank()) {
                    stages.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(compiledTrigger);
                }
            }
        }
        return new QuestTriggerIndex(ordered, byEvent, globalByEvent, byEventAndStage, continuous);
    }

    public static String normalizeEvent(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static QuestTriggerEventDescriptor register(
            String id,
            QuestDefinition.TriggerEvent event,
            Set<String> aliases,
            boolean continuous,
            long defaultCooldownTicks) {
        return new QuestTriggerEventDescriptor(id, event, aliases, continuous, defaultCooldownTicks);
    }

    private static Set<String> aliases(String... values) {
        Set<String> aliases = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeEvent(value);
                if (!normalized.isBlank()) {
                    aliases.add(normalized);
                }
            }
        }
        return Set.copyOf(aliases);
    }

    private static Map<String, QuestTriggerEventDescriptor> descriptorsByAlias() {
        Map<String, QuestTriggerEventDescriptor> descriptors = new HashMap<>();
        for (QuestTriggerEventDescriptor descriptor : REGISTRATIONS) {
            descriptors.put(descriptor.id(), descriptor);
            for (String alias : descriptor.aliases()) {
                descriptors.put(alias, descriptor);
            }
        }
        return Map.copyOf(descriptors);
    }

    private static Map<QuestDefinition.TriggerEvent, QuestTriggerEventDescriptor> descriptorsByEvent() {
        Map<QuestDefinition.TriggerEvent, QuestTriggerEventDescriptor> descriptors =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        for (QuestTriggerEventDescriptor descriptor : REGISTRATIONS) {
            descriptors.put(descriptor.event(), descriptor);
        }
        return Map.copyOf(descriptors);
    }
}
