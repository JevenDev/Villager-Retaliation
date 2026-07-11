package com.jvn.villagerretaliation.quest.debug;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class QuestDebugFormatter {
    private QuestDebugFormatter() {
    }

    public static String header(QuestDefinition definition) {
        return "Quest " + definition.id() + " | " + definition.title();
    }

    public static String identityLine(
            QuestDefinition definition,
            String parentState) {
        return "identity questline=" + blankAs(definition.questline(), "none")
                + " tags=" + stringSet(definition.tags())
                + " parent=" + parentState
                + " prerequisites=" + definition.prerequisites()
                + " objectives=" + definition.objectives().size()
                + " triggers=" + definition.triggers().size();
    }

    public static String rulesLine(QuestDefinition.Rules rules) {
        return "rules repeatable=" + rules.repeatable()
                + " max_starts=" + rules.maxStarts()
                + " max_completions=" + rules.maxCompletions()
                + " completion_scope=" + enumName(rules.completionScope())
                + " completion_cooldown_ticks=" + rules.completionCooldownTicks()
                + " locked_to_villager=" + rules.lockedToVillager()
                + " cross_villager=" + rules.crossVillagerCompatible();
    }

    public static String activeStateLine(QuestDefinition.Rules rules) {
        return "active_state conditions=" + rules.activeState().conditions().size()
                + " hide_when_unmet=" + rules.activeState().hideWhenUnmet()
                + " pause_progress_when_unmet=" + rules.activeState().pauseProgressWhenUnmet()
                + " expiration_enabled=" + rules.expiration().enabled()
                + " expiration_after_ticks=" + rules.expiration().afterTicks();
    }

    public static String branchingLine(QuestDefinition.Branching branching) {
        return "branching exclusive_group=" + resource(branching.exclusiveGroup())
                + " exclusive_on=" + enumName(branching.exclusiveOn())
                + " blocks_on_start=" + resourceSet(branching.blocksOnStart())
                + " blocks_on_completion=" + resourceSet(branching.blocksOnCompletion());
    }

    public static String parentState(ResourceLocation parentId, VillagerQuestSavedData.QuestProgress parentProgress) {
        if (parentId == null) {
            return "none";
        }
        if (parentProgress == null) {
            return parentId + "(not_started)";
        }
        String completed = parentProgress.completionCount() > 0 ? ",completed=true" : "";
        return parentId + "(" + enumName(parentProgress.state()) + completed + ")";
    }

    public static String progressLine(ProgressLine input) {
        if (!input.saved()) {
            return "progress saved=false state=not_started ready=false active_conditions=n/a branch_locked=false";
        }
        return "progress saved=true state=" + enumName(input.state())
                + " stage=" + blankAs(input.stage(), "none")
                + " starts=" + input.starts()
                + " completions=" + input.completions()
                + " abandons=" + input.abandons()
                + " ready=" + input.ready()
                + " active_conditions=" + input.activeConditions()
                + " branch_locked=" + input.branchLocked();
    }

    public static String issuerLine(IssuerLine input) {
        return "issuer status=" + input.status()
                + " id=" + blankAs(input.id(), "none")
                + " name=" + blankAs(input.name(), "unknown")
                + " profession=" + blankAs(input.profession(), "unknown")
                + " dimension=" + input.dimension()
                + " pos=" + input.pos();
    }

    public static String targetProgressLine(TargetProgressLine input) {
        return "target visited=" + input.visited()
                + " proof=" + input.proof()
                + " objective=" + blankAs(input.objective(), "none")
                + " dimension=" + input.dimension()
                + " pos=" + input.pos();
    }

    public static String timesLine(VillagerQuestSavedData.QuestProgress progress) {
        return "times started=" + progress.startedGameTime()
                + " completed=" + progress.completedGameTime()
                + " abandoned=" + progress.abandonedGameTime()
                + " expired=" + progress.expiredGameTime()
                + " failed=" + progress.failedGameTime()
                + " failure_reason=" + blankAs(progress.failureReason(), "none")
                + " consumed_reason=" + blankAs(progress.consumedReason(), "none");
    }

    public static String choiceHistoryLine(VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.choiceHistory().isEmpty()) {
            return "choice_history entries=0";
        }
        VillagerQuestSavedData.ChoiceHistoryEntry latest = progress.lastChoice();
        return "choice_history entries=" + progress.choiceHistory().size()
                + " latest_scene=" + blankAs(latest.scenePath(), "none")
                + " latest_response=" + blankAs(latest.responseId(), "none")
                + " latest_prior_stage=" + blankAs(latest.priorStage(), "none")
                + " latest_next_stage=" + blankAs(latest.nextStage(), "none")
                + " latest_time=" + latest.gameTime();
    }

    public static String providerRebindHistoryLine(VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.providerRebindHistory().isEmpty()) {
            return "provider_rebind_history entries=0";
        }
        VillagerQuestSavedData.ProviderRebindHistoryEntry latest = progress.providerRebindHistory().getLast();
        return "provider_rebind_history entries=" + progress.providerRebindHistory().size()
                + " latest_previous=" + latest.previousProviderId()
                + " latest_replacement=" + latest.newProviderId()
                + " latest_reason=" + latest.reason()
                + " latest_time=" + latest.gameTime();
    }

    public static String pendingLifecycleEventsLine(VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.pendingLifecycleEvents().isEmpty()) {
            return "pending_lifecycle_events entries=0";
        }
        return "pending_lifecycle_events entries=" + progress.pendingLifecycleEvents().size()
                + " events=" + progress.pendingLifecycleEvents().stream()
                        .map(QuestTriggerRegistry::canonicalEventId)
                        .sorted()
                        .toList();
    }

    public static String targetDefinitionLine(QuestDefinition.Target target) {
        return "target_definition structure=" + target.structure()
                + " dimension=" + dimension(target.dimension())
                + " search_radius=" + target.searchRadius()
                + " discovery_radius=" + target.discoveryRadius()
                + " proof_item=" + resource(target.proofItem());
    }

    public static String objectiveLine(
            ResourceLocation questId,
            QuestDefinition.Objective objective,
            ObjectiveLineState state) {
        List<String> parts = new ArrayList<>();
        parts.add("objective " + objective.id());
        parts.add("type=" + enumName(objective.type()));
        parts.add("optional=" + objective.optional());
        parts.add("complete=" + state.complete());
        switch (objective.type()) {
            case STRUCTURE_VISIT -> {
                parts.add("structure=" + resource(objective.structure()));
                parts.add("dimension=" + dimension(objective.dimension()));
                parts.add("pieces=" + stringList(objective.pieces()));
                parts.add("search_radius=" + objective.searchRadius());
                parts.add("discovery_radius=" + objective.discoveryRadius());
            }
            case LOCATION_VISIT -> addLocation(parts, objective);
            case ITEM_CHECK -> {
                parts.add("item=" + resource(objective.item()));
                parts.add("current=" + state.itemCount());
                parts.add("count=" + objective.count());
                parts.add("consume=" + objective.consume());
                parts.add("enchantments=" + objective.itemRequirements().enchantments().size());
                parts.add("custom_data=" + objective.itemRequirements().hasCustomData());
            }
            case MOB_KILL -> {
                addCounter(parts, state.counter(), objective.count());
                parts.add("entities=" + resourceSet(objective.entityTypes()));
                parts.add("entity_tags=" + resourceSet(objective.entityTags()));
                addLocation(parts, objective);
            }
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT -> {
                addCounter(parts, state.counter(), objective.count());
                parts.add("blocks=" + resourceSet(objective.blockTypes()));
                parts.add("block_tags=" + resourceSet(objective.blockTags()));
                addLocation(parts, objective);
            }
            case MEMORY_EVENT -> {
                addCounter(parts, state.counter(), objective.count());
                parts.add("memory_tags=" + resourceSet(objective.memoryTags()));
                addLocation(parts, objective);
            }
            case TRADE -> {
                addCounter(parts, state.counter(), objective.count());
                parts.add("result_item=" + resource(objective.item()));
                addLocation(parts, objective);
            }
            case GIFT -> {
                addCounter(parts, state.counter(), objective.count());
                parts.add("item=" + resource(objective.item()));
                parts.add("reactions=" + stringSet(objective.giftReactions()));
            }
            case REPUTATION -> {
                parts.add("current=" + state.reputation());
                parts.add("levels=" + enumSet(objective.reputationLevels()));
                parts.add("min=" + (objective.minReputation() == null ? "none" : objective.minReputation()));
                parts.add("max=" + (objective.maxReputation() == null ? "none" : objective.maxReputation()));
            }
            case CHOICE -> {
                parts.add("scope=" + enumName(objective.factScope()));
                parts.add("quest=" + resource(objective.factQuestId() == null ? questId : objective.factQuestId()));
                parts.add("key=" + blankAs(objective.factKey(), "none"));
                parts.add("choices=" + stringSet(objective.factValues()));
                if (!state.scopeKey().isBlank()) {
                    parts.add("scope_key=" + state.scopeKey());
                }
            }
            case FACT -> {
                parts.add("scope=" + enumName(objective.factScope()));
                parts.add("quest=" + resource(objective.factQuestId() == null ? questId : objective.factQuestId()));
                parts.add("tags=" + resourceSet(objective.factTags()));
                parts.add("key=" + blankAs(objective.factKey(), "none"));
                parts.add("values=" + stringSet(objective.factValues()));
                parts.add("min=" + (objective.factMin() == null ? "none" : objective.factMin()));
                parts.add("max=" + (objective.factMax() == null ? "none" : objective.factMax()));
                if (!state.scopeKey().isBlank()) {
                    parts.add("scope_key=" + state.scopeKey());
                }
            }
            case CONDITION -> {
                parts.add("conditions=" + objective.conditions().size());
                parts.add("condition_state=" + state.conditionState());
            }
        }
        return String.join(" ", parts);
    }

    public static String inventoryCacheLine(InventoryCacheLine input) {
        if (!input.enabled()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add("inventory_cache");
        parts.add("state=" + (input.warm() ? "warm" : "cold"));
        parts.add("change_count=" + input.changeCount());
        parts.add("item_objectives=" + input.itemObjectives());
        parts.add("exact_item_objectives=" + input.exactItemObjectives());
        parts.add("proof_item=" + input.proofItem());
        if (input.warm()) {
            parts.add("simple_item_entries=" + input.simpleItemEntries());
            parts.add("exact_objective_entries=" + input.exactObjectiveEntries());
            parts.add("rebuilt_age_ticks=" + input.rebuiltAgeTicks());
            parts.add("simple_scan_slots=" + input.simpleScanSlots());
            parts.add("simple_lookups=" + input.simpleLookups());
            parts.add("exact_lookups=" + input.exactLookups());
            parts.add("exact_cache_misses=" + input.exactCacheMisses());
            parts.add("exact_scan_slots=" + input.exactScanSlots());
        }
        return String.join(" ", parts);
    }

    public static String resource(ResourceLocation id) {
        return id == null ? "none" : id.toString();
    }

    public static String dimension(ResourceKey<Level> dimension) {
        return dimension == null ? "any" : dimension.location().toString();
    }

    public static String pos(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static String enumName(Enum<?> value) {
        return value == null ? "none" : value.name().toLowerCase(Locale.ROOT);
    }

    public static String enumSet(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(QuestDebugFormatter::enumName)
                .sorted()
                .toList()
                .toString();
    }

    public static String resourceSet(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList()
                .toString();
    }

    public static String stringSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .sorted()
                .toList()
                .toString();
    }

    public static String stringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.toString();
    }

    public static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void addCounter(List<String> parts, int current, int required) {
        parts.add("counter=" + current + "/" + required);
    }

    private static void addLocation(List<String> parts, QuestDefinition.Objective objective) {
        parts.add("dimension=" + dimension(objective.dimension()));
        if (objective.location() != null) {
            parts.add("location=" + pos(objective.location()));
            parts.add("radius=" + objective.radius());
        }
    }

    public record ProgressLine(
            boolean saved,
            VillagerQuestSavedData.QuestState state,
            String stage,
            int starts,
            int completions,
            int abandons,
            String ready,
            String activeConditions,
            boolean branchLocked
    ) {
        public ProgressLine {
            ready = ready == null ? "false" : ready;
            activeConditions = activeConditions == null ? "none" : activeConditions;
        }
    }

    public record IssuerLine(
            String status,
            String id,
            String name,
            String profession,
            String dimension,
            String pos
    ) {
        public IssuerLine {
            status = status == null ? "unknown" : status;
            id = id == null ? "none" : id;
            dimension = dimension == null ? "any" : dimension;
            pos = pos == null ? "none" : pos;
        }
    }

    public record TargetProgressLine(
            boolean visited,
            boolean proof,
            String objective,
            String dimension,
            String pos
    ) {
        public TargetProgressLine {
            dimension = dimension == null ? "any" : dimension;
            pos = pos == null ? "none" : pos;
        }
    }

    public record ObjectiveLineState(
            boolean complete,
            int counter,
            int itemCount,
            int reputation,
            String scopeKey,
            String conditionState
    ) {
        public ObjectiveLineState {
            counter = Math.max(0, counter);
            itemCount = Math.max(0, itemCount);
            scopeKey = scopeKey == null ? "" : scopeKey;
            conditionState = conditionState == null ? "unknown" : conditionState;
        }
    }

    public record InventoryCacheLine(
            boolean enabled,
            boolean warm,
            int changeCount,
            int itemObjectives,
            int exactItemObjectives,
            boolean proofItem,
            int simpleItemEntries,
            int exactObjectiveEntries,
            long rebuiltAgeTicks,
            int simpleScanSlots,
            int simpleLookups,
            int exactLookups,
            int exactCacheMisses,
            int exactScanSlots
    ) {
    }
}
