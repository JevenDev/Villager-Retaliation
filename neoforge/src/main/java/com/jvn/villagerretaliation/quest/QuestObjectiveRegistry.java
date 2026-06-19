package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
                    new MobKillObjectiveType()),
            register(
                    "block_break",
                    QuestDefinition.ObjectiveType.BLOCK_BREAK,
                    aliases("break_block", "mine_block", "mine"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    new BlockObjectiveType(QuestObjectiveEventKind.BLOCK_BREAK)),
            register(
                    "block_place",
                    QuestDefinition.ObjectiveType.BLOCK_PLACE,
                    aliases("place_block", "place"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    new BlockObjectiveType(QuestObjectiveEventKind.BLOCK_PLACE)),
            register(
                    "block_interact",
                    QuestDefinition.ObjectiveType.BLOCK_INTERACT,
                    aliases("interact_block", "right_click_block", "use_block", "block_use"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    new BlockObjectiveType(QuestObjectiveEventKind.BLOCK_INTERACT)),
            register(
                    "memory_event",
                    QuestDefinition.ObjectiveType.MEMORY_EVENT,
                    aliases("village_event", "village_memory", "memory", "event"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    new MemoryEventObjectiveType()),
            register(
                    "trade",
                    QuestDefinition.ObjectiveType.TRADE,
                    aliases("villager_trade", "trading", "merchant_trade"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    new TradeObjectiveType()),
            register(
                    "gift",
                    QuestDefinition.ObjectiveType.GIFT,
                    aliases("give_gift", "gift_given"),
                    requirements(QuestObjectiveRequirement.EVENT, QuestObjectiveRequirement.COUNTER),
                    new GiftObjectiveType()),
            register(
                    "reputation",
                    QuestDefinition.ObjectiveType.REPUTATION,
                    aliases("rep", "reputation_level", "trust"),
                    requirements(QuestObjectiveRequirement.POLLING, QuestObjectiveRequirement.LIVE_CONTEXT),
                    new ReputationObjectiveType()),
            register(
                    "choice",
                    QuestDefinition.ObjectiveType.CHOICE,
                    aliases("dialogue_choice", "branch_choice", "quest_choice"),
                    requirements(QuestObjectiveRequirement.POLLING),
                    new ChoiceObjectiveType()),
            register(
                    "fact",
                    QuestDefinition.ObjectiveType.FACT,
                    aliases("quest_fact", "quest_tag", "quest_variable", "quest_counter", "quest_stage", "stage"),
                    requirements(QuestObjectiveRequirement.POLLING),
                    new FactObjectiveType()),
            register(
                    "condition",
                    QuestDefinition.ObjectiveType.CONDITION,
                    aliases(),
                    requirements(QuestObjectiveRequirement.POLLING, QuestObjectiveRequirement.LIVE_CONTEXT),
                    new ConditionObjectiveType())
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

    public static Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
        return implementation(objective)
                .map(type -> type.eventKinds(objective))
                .orElse(Set.of());
    }

    public static Set<ResourceLocation> eventSubscriptionKeys(QuestDefinition.Objective objective) {
        return implementation(objective)
                .map(type -> type.eventSubscriptionKeys(objective))
                .orElse(Set.of());
    }

    public static boolean matchesEvent(
            QuestObjectiveEvaluationContext context,
            QuestDefinition.Objective objective,
            QuestObjectiveEvent event) {
        if (event == null || !eventKinds(objective).contains(event.kind())) {
            return false;
        }
        return implementation(objective)
                .map(type -> type.matchesEvent(context, objective, event))
                .orElse(false);
    }

    public static QuestObjectiveEventTrace traceEventMatches(
            QuestObjectiveEvaluationContext context,
            Iterable<QuestDefinition.Objective> objectives,
            QuestObjectiveEvent event) {
        if (objectives == null || event == null) {
            return new QuestObjectiveEventTrace(0, 0);
        }
        int evaluated = 0;
        int matched = 0;
        for (QuestDefinition.Objective objective : objectives) {
            if (!eventKinds(objective).contains(event.kind())) {
                continue;
            }
            evaluated++;
            if (matchesEvent(context, objective, event)) {
                matched++;
            }
        }
        return new QuestObjectiveEventTrace(evaluated, matched);
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

    private abstract static class CounterEventObjectiveType implements QuestObjectiveType<Void> {
        @Override
        public QuestObjectiveResult evaluate(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            int count = context == null || context.progress() == null
                    ? 0
                    : context.progress().objectiveCounter(objective.id());
            float progress = Mth.clamp((float) count / (float) objective.count(), 0.0F, 1.0F);
            return count >= objective.count()
                    ? QuestObjectiveResult.complete("event objective complete")
                    : QuestObjectiveResult.incomplete(progress, "event objective incomplete");
        }
    }

    private static final class MobKillObjectiveType extends CounterEventObjectiveType {
        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.entityTypes().isEmpty() && objective.entityTags().isEmpty()
                    ? Optional.of("mob_kill objective must define entity, entities, entity_tag, or entity_tags.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "hunt";
        }

        @Override
        public Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
            return Set.of(QuestObjectiveEventKind.MOB_KILL);
        }

        @Override
        public boolean matchesEvent(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveEvent event) {
            if (event.killedEntity() == null
                    || !matchesDimensionAndLocation(context, event.killedEntity().blockPosition(), objective)
                    || (objective.entityTypes().isEmpty() && objective.entityTags().isEmpty())) {
                return false;
            }
            ResourceLocation killedType = BuiltInRegistries.ENTITY_TYPE.getKey(event.killedEntity().getType());
            if (killedType != null && objective.entityTypes().contains(killedType)) {
                return true;
            }
            for (ResourceLocation tagId : objective.entityTags()) {
                TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
                if (event.killedEntity().getType().is(tag)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class BlockObjectiveType extends CounterEventObjectiveType {
        private final QuestObjectiveEventKind eventKind;

        private BlockObjectiveType(QuestObjectiveEventKind eventKind) {
            this.eventKind = eventKind;
        }

        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.blockTypes().isEmpty() && objective.blockTags().isEmpty()
                    ? Optional.of(objective.type().name().toLowerCase(Locale.ROOT)
                            + " objective must define block, blocks, block_tag, or block_tags.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return switch (this.eventKind) {
                case BLOCK_BREAK -> "break";
                case BLOCK_PLACE -> "build";
                case BLOCK_INTERACT -> "interact";
                default -> "";
            };
        }

        @Override
        public Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
            return Set.of(this.eventKind);
        }

        @Override
        public boolean matchesEvent(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveEvent event) {
            BlockState state = event.blockState();
            if (state == null
                    || state.isAir()
                    || !matchesDimensionAndLocation(context, event.blockPos(), objective)
                    || (objective.blockTypes().isEmpty() && objective.blockTags().isEmpty())) {
                return false;
            }
            ResourceLocation blockType = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockType != null && objective.blockTypes().contains(blockType)) {
                return true;
            }
            for (ResourceLocation tagId : objective.blockTags()) {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class MemoryEventObjectiveType extends CounterEventObjectiveType {
        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.memoryTags().isEmpty()
                    ? Optional.of("memory_event objective must define memory, memory_event, memory_tags, event, or events.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "event";
        }

        @Override
        public Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
            return Set.of(QuestObjectiveEventKind.MEMORY_EVENT);
        }

        @Override
        public Set<ResourceLocation> eventSubscriptionKeys(QuestDefinition.Objective objective) {
            return objective.memoryTags();
        }

        @Override
        public boolean matchesEvent(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveEvent event) {
            if (context == null
                    || context.player() == null
                    || event.memoryEvent() == null
                    || event.memoryEvent().tagId() == null
                    || event.memoryEvent().pos() == null
                    || !context.player().getUUID().equals(event.memoryEvent().playerId())
                    || !matchesDimensionAndLocation(context, event.memoryEvent().pos(), objective)) {
                return false;
            }
            return objective.memoryTags().contains(event.memoryEvent().tagId());
        }
    }

    private static final class TradeObjectiveType extends CounterEventObjectiveType {
        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "trade";
        }

        @Override
        public Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
            return Set.of(QuestObjectiveEventKind.TRADE);
        }

        @Override
        public boolean matchesEvent(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveEvent event) {
            if (event.villager() == null
                    || event.offer() == null
                    || !matchesDimensionAndLocation(context, event.villager().blockPosition(), objective)) {
                return false;
            }
            return objective.item() == null || context.matchesItem(objective, event.offer().getResult());
        }
    }

    private static final class GiftObjectiveType extends CounterEventObjectiveType {
        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "gift";
        }

        @Override
        public Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
            return Set.of(QuestObjectiveEventKind.GIFT);
        }

        @Override
        public boolean matchesEvent(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveEvent event) {
            ItemStack stack = event.itemStack();
            if (stack.isEmpty() || event.giftReaction() == null) {
                return false;
            }
            if (!objective.giftReactions().isEmpty()
                    && !objective.giftReactions().contains(event.giftReaction().name().toLowerCase(Locale.ROOT))) {
                return false;
            }
            return objective.item() == null || context.matchesItem(objective, stack);
        }
    }

    private static final class ReputationObjectiveType implements QuestObjectiveType<Void> {
        @Override
        public QuestObjectiveResult evaluate(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            int reputation = context == null ? 0 : context.reputationValue(objective);
            boolean complete = matchesReputation(objective, reputation);
            return complete
                    ? QuestObjectiveResult.complete("reputation requirement met")
                    : QuestObjectiveResult.incomplete(0.0F, "reputation requirement unmet");
        }

        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.reputationLevels().isEmpty()
                    && objective.minReputation() == null
                    && objective.maxReputation() == null
                    ? Optional.of("reputation objective must define level, levels, min_reputation, max_reputation, min, or max.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "reputation";
        }

        @Override
        public Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
            return Set.of(QuestObjectiveEventKind.REPUTATION);
        }

        @Override
        public boolean matchesEvent(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveEvent event) {
            if (event.villager() != null) {
                if (context == null
                        || context.progress() == null
                        || context.progress().startedVillagerId() == null
                        || !event.villager().getUUID().equals(context.progress().startedVillagerId())) {
                    return false;
                }
            }
            int reputation = event.reputationValue() == null
                    ? context == null ? 0 : context.reputationValue(objective)
                    : event.reputationValue();
            return matchesReputation(objective, reputation);
        }

        private static boolean matchesReputation(QuestDefinition.Objective objective, int reputation) {
            VillagerReputationLevel reputationLevel = VillagerReputationLevel.fromReputation(reputation);
            if (!objective.reputationLevels().isEmpty() && !objective.reputationLevels().contains(reputationLevel)) {
                return false;
            }
            if (objective.minReputation() != null && reputation < objective.minReputation()) {
                return false;
            }
            return objective.maxReputation() == null || reputation <= objective.maxReputation();
        }
    }

    private static boolean matchesDimensionAndLocation(
            QuestObjectiveEvaluationContext context,
            BlockPos pos,
            QuestDefinition.Objective objective) {
        if (context == null || context.level() == null || pos == null) {
            return false;
        }
        if (objective.dimension() != null && context.level().dimension() != objective.dimension()) {
            return false;
        }
        if (objective.location() == null) {
            return true;
        }
        double radius = Math.max(1, objective.radius());
        return pos.distSqr(objective.location()) <= radius * radius;
    }

    private abstract static class LogicalObjectiveType implements QuestObjectiveType<Void> {
        @Override
        public QuestObjectiveResult evaluate(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            if (context == null) {
                return QuestObjectiveResult.incomplete(0.0F, "logical context unavailable");
            }
            boolean complete = matches(context, objective);
            return complete
                    ? QuestObjectiveResult.complete(completeMessage())
                    : QuestObjectiveResult.incomplete(0.0F, incompleteMessage());
        }

        @Override
        public QuestObjectiveDebugState debugState(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective,
                QuestObjectiveResult result) {
            return context == null ? QuestObjectiveDebugState.EMPTY : context.debugState(objective);
        }

        protected abstract boolean matches(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective);

        protected abstract String completeMessage();

        protected abstract String incompleteMessage();
    }

    private static class FactObjectiveType extends LogicalObjectiveType {
        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return factDefinitionMissing(objective)
                    ? Optional.of("fact objective must define tag, tags, key, variable, counter, stage, or stages.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "fact";
        }

        @Override
        protected boolean matches(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            return context.matchesFact(objective);
        }

        @Override
        protected String completeMessage() {
            return "fact objective complete";
        }

        @Override
        protected String incompleteMessage() {
            return "fact objective incomplete";
        }
    }

    private static final class ChoiceObjectiveType extends FactObjectiveType {
        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return factDefinitionMissing(objective)
                    ? Optional.of("choice objective must define choice, choices, value, values, or a fact key.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "choice";
        }

        @Override
        protected String completeMessage() {
            return "choice objective complete";
        }

        @Override
        protected String incompleteMessage() {
            return "choice objective incomplete";
        }
    }

    private static final class ConditionObjectiveType extends LogicalObjectiveType {
        @Override
        public Optional<String> validationError(QuestDefinition.Objective objective) {
            return objective.conditions().isEmpty()
                    ? Optional.of("condition objective must define conditions.")
                    : Optional.empty();
        }

        @Override
        public String trackerStepKey(QuestDefinition.Objective objective) {
            return "inactive";
        }

        @Override
        protected boolean matches(
                QuestObjectiveEvaluationContext context,
                QuestDefinition.Objective objective) {
            return context.matchesConditions(objective);
        }

        @Override
        protected String completeMessage() {
            return "condition objective complete";
        }

        @Override
        protected String incompleteMessage() {
            return "condition objective incomplete";
        }
    }

    private static boolean factDefinitionMissing(QuestDefinition.Objective objective) {
        return objective.factTags().isEmpty()
                && objective.factKey().isBlank()
                && objective.factValues().isEmpty()
                && objective.factMin() == null
                && objective.factMax() == null;
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
