package com.jvn.villagerretaliation.quest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public final class QuestObjectiveRegistry {
    private static final List<QuestObjectiveTypeDescriptor<?>> REGISTRATIONS = List.of(
            register(
                    "structure_visit",
                    QuestDefinition.ObjectiveType.STRUCTURE_VISIT,
                    aliases(),
                    requirements(
                            QuestObjectiveRequirement.POLLING,
                            QuestObjectiveRequirement.STRUCTURE_TARGET,
                            QuestObjectiveRequirement.PLAYER_POSITION),
                    new StructureVisitObjectiveType()),
            register(
                    "location_visit",
                    QuestDefinition.ObjectiveType.LOCATION_VISIT,
                    aliases("coordinate", "coordinates", "coords", "region_visit"),
                    requirements(QuestObjectiveRequirement.POLLING, QuestObjectiveRequirement.PLAYER_POSITION),
                    new LocationVisitObjectiveType()),
            register(
                    "item_check",
                    QuestDefinition.ObjectiveType.ITEM_CHECK,
                    aliases(),
                    requirements(QuestObjectiveRequirement.POLLING, QuestObjectiveRequirement.INVENTORY),
                    new ItemCheckObjectiveType()),
            register(
                    "mob_kill",
                    QuestDefinition.ObjectiveType.MOB_KILL,
                    aliases("entity_kill", "kill"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "block_break",
                    QuestDefinition.ObjectiveType.BLOCK_BREAK,
                    aliases("break_block", "mine_block", "mine"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "block_place",
                    QuestDefinition.ObjectiveType.BLOCK_PLACE,
                    aliases("place_block", "place"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "block_interact",
                    QuestDefinition.ObjectiveType.BLOCK_INTERACT,
                    aliases("interact_block", "right_click_block", "use_block", "block_use"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "memory_event",
                    QuestDefinition.ObjectiveType.MEMORY_EVENT,
                    aliases("village_event", "village_memory", "memory", "event"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "trade",
                    QuestDefinition.ObjectiveType.TRADE,
                    aliases("villager_trade", "trading", "merchant_trade"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "gift",
                    QuestDefinition.ObjectiveType.GIFT,
                    aliases("give_gift", "gift_given"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    null),
            register(
                    "reputation",
                    QuestDefinition.ObjectiveType.REPUTATION,
                    aliases("rep", "reputation_level", "trust"),
                    requirements(QuestObjectiveRequirement.POLLING, QuestObjectiveRequirement.LIVE_CONTEXT),
                    null),
            register(
                    "choice",
                    QuestDefinition.ObjectiveType.CHOICE,
                    aliases("dialogue_choice", "branch_choice", "quest_choice"),
                    requirements(QuestObjectiveRequirement.POLLING),
                    null),
            register(
                    "fact",
                    QuestDefinition.ObjectiveType.FACT,
                    aliases("quest_fact", "quest_tag", "quest_variable", "quest_counter", "quest_stage", "stage"),
                    requirements(QuestObjectiveRequirement.POLLING),
                    null),
            register(
                    "condition",
                    QuestDefinition.ObjectiveType.CONDITION,
                    aliases(),
                    requirements(QuestObjectiveRequirement.POLLING, QuestObjectiveRequirement.LIVE_CONTEXT),
                    null)
    );
    private static final Map<String, QuestObjectiveTypeDescriptor<?>> BY_ALIAS = descriptorsByAlias();
    private static final Map<QuestDefinition.ObjectiveType, QuestObjectiveTypeDescriptor<?>> BY_TYPE = descriptorsByType();

    private QuestObjectiveRegistry() {
    }

    public static List<QuestObjectiveTypeDescriptor<?>> descriptors() {
        return REGISTRATIONS;
    }

    public static QuestDefinition.ObjectiveType objectiveTypeBySerializedName(String value) {
        QuestObjectiveTypeDescriptor<?> descriptor = BY_ALIAS.get(normalizeType(value));
        return descriptor == null ? null : descriptor.objectiveType();
    }

    public static String canonicalTypeId(String type) {
        String normalized = normalizeType(type);
        QuestObjectiveTypeDescriptor<?> descriptor = BY_ALIAS.get(normalized);
        return descriptor == null ? normalized : descriptor.id();
    }

    public static String canonicalTypeId(QuestDefinition.Objective objective) {
        if (objective == null) {
            return "";
        }
        QuestObjectiveTypeDescriptor<?> descriptor = BY_TYPE.get(objective.type());
        return descriptor == null ? objective.type().name().toLowerCase(Locale.ROOT) : descriptor.id();
    }

    public static Set<QuestObjectiveRequirement> requirements(QuestDefinition.Objective objective) {
        if (objective == null) {
            return Set.of();
        }
        QuestObjectiveTypeDescriptor<?> descriptor = BY_TYPE.get(objective.type());
        return descriptor == null ? Set.of() : descriptor.requirements();
    }

    public static Optional<String> validationError(QuestDefinition.Objective objective) {
        return implementation(objective).flatMap(type -> type.validationError(objective));
    }

    public static Optional<QuestObjectiveResult> evaluate(
            QuestObjectiveEvaluationContext context,
            QuestDefinition.Objective objective) {
        return implementation(objective).map(type -> type.evaluate(context, objective));
    }

    public static String trackerStepKey(QuestDefinition.Objective objective) {
        return implementation(objective)
                .map(type -> type.trackerStepKey(objective))
                .filter(step -> step != null && !step.isBlank())
                .orElse("");
    }

    public static QuestObjectiveDebugState debugState(
            QuestObjectiveEvaluationContext context,
            QuestDefinition.Objective objective,
            QuestObjectiveResult result) {
        return implementation(objective)
                .map(type -> type.debugState(context, objective, result))
                .orElse(QuestObjectiveDebugState.EMPTY);
    }

    public static boolean requiresLocatedTarget(QuestDefinition.Objective objective) {
        return implementation(objective)
                .map(type -> type.requiresLocatedTarget(objective))
                .orElse(false);
    }

    public static boolean requiresItemHandIn(QuestDefinition.Objective objective) {
        return implementation(objective)
                .map(type -> type.requiresItemHandIn(objective))
                .orElse(false);
    }

    static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<QuestObjectiveType<?>> implementation(QuestDefinition.Objective objective) {
        if (objective == null) {
            return Optional.empty();
        }
        QuestObjectiveTypeDescriptor<?> descriptor = BY_TYPE.get(objective.type());
        return descriptor == null || descriptor.implementation() == null
                ? Optional.empty()
                : Optional.of(descriptor.implementation());
    }

    private static QuestObjectiveTypeDescriptor<?> register(
            String id,
            QuestDefinition.ObjectiveType type,
            Set<String> aliases,
            Set<QuestObjectiveRequirement> requirements,
            QuestObjectiveType<?> implementation) {
        return new QuestObjectiveTypeDescriptor<>(id, type, aliases, requirements, implementation);
    }

    private static Map<String, QuestObjectiveTypeDescriptor<?>> descriptorsByAlias() {
        Map<String, QuestObjectiveTypeDescriptor<?>> descriptors = new LinkedHashMap<>();
        for (QuestObjectiveTypeDescriptor<?> descriptor : REGISTRATIONS) {
            descriptors.put(descriptor.id(), descriptor);
            for (String alias : descriptor.aliases()) {
                descriptors.put(normalizeType(alias), descriptor);
            }
        }
        return Map.copyOf(descriptors);
    }

    private static Map<QuestDefinition.ObjectiveType, QuestObjectiveTypeDescriptor<?>> descriptorsByType() {
        Map<QuestDefinition.ObjectiveType, QuestObjectiveTypeDescriptor<?>> descriptors = new HashMap<>();
        for (QuestObjectiveTypeDescriptor<?> descriptor : REGISTRATIONS) {
            descriptors.put(descriptor.objectiveType(), descriptor);
        }
        return Map.copyOf(descriptors);
    }

    private static Set<String> aliases(String... aliases) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (aliases != null) {
            for (String alias : aliases) {
                String normalized = normalizeType(alias);
                if (!normalized.isBlank()) {
                    result.add(normalized);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<QuestObjectiveRequirement> requirements(QuestObjectiveRequirement... requirements) {
        return requirements == null || requirements.length == 0
                ? Set.of()
                : Set.of(requirements);
    }

    private static final class StructureVisitObjectiveType implements QuestObjectiveType<Void> {
        @Override
        public QuestObjectiveResult evaluate(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            if (context == null || context.level() == null || context.player() == null) {
                return QuestObjectiveResult.incomplete(0.0F, "live context unavailable");
            }
            boolean complete = VillagerQuestTargets.isAtObjectiveTarget(
                    context.level(),
                    context.player().blockPosition(),
                    objective,
                    context.progress());
            return complete
                    ? QuestObjectiveResult.complete("structure target reached")
                    : QuestObjectiveResult.incomplete(0.0F, "structure target not reached");
        }

        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.structure() == null
                    ? Optional.of("structure_visit objective must define structure.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "travel";
        }

        @Override
        public boolean requiresLocatedTarget(QuestDefinition.Objective objective) {
            return objective.structure() != null;
        }
    }

    private static final class LocationVisitObjectiveType implements QuestObjectiveType<Void> {
        @Override
        public QuestObjectiveResult evaluate(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            if (context == null || context.level() == null || context.player() == null) {
                return QuestObjectiveResult.incomplete(0.0F, "live context unavailable");
            }
            boolean complete = isAtLocationObjective(context, objective);
            return complete
                    ? QuestObjectiveResult.complete("location reached")
                    : QuestObjectiveResult.incomplete(0.0F, "location not reached");
        }

        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.location() == null
                    ? Optional.of("location_visit objective must define x, y, and z.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "travel";
        }

        private static boolean isAtLocationObjective(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            BlockPos location = objective.location();
            if (location == null) {
                return false;
            }
            if (objective.dimension() != null && context.level().dimension() != objective.dimension()) {
                return false;
            }
            double radius = Math.max(1, objective.radius());
            return context.player().blockPosition().distSqr(location) <= radius * radius;
        }
    }

    private static final class ItemCheckObjectiveType implements QuestObjectiveType<Void> {
        @Override
        public QuestObjectiveResult evaluate(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            int count = context == null ? 0 : context.itemCount(objective);
            float progress = Mth.clamp((float) count / (float) objective.count(), 0.0F, 1.0F);
            return count >= objective.count()
                    ? QuestObjectiveResult.complete("item requirement met")
                    : QuestObjectiveResult.incomplete(progress, "item requirement unmet");
        }

        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.item() == null
                    ? Optional.of("item_check objective must define item.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "proof";
        }

        @Override
        public QuestObjectiveDebugState debugState(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveResult result) {
            return new QuestObjectiveDebugState(context == null ? 0 : context.itemCount(objective), result.message());
        }

        @Override
        public boolean requiresItemHandIn(QuestDefinition.Objective objective) {
            return objective.consume() && objective.item() != null;
        }
    }
}
