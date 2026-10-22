package com.jvn.villagerretaliation.quest.conditions;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionCapability;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionLeafAccess;
import com.jvn.villagerretaliation.mood.VillagerMoodSavedData;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.dialogue.conditions.QuestConditionQueries;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class VillagerConditionAccess implements ConditionLeafAccess {
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 60L * 20L;
    private static final long BROKEN_BED_MEMORY_TICKS = 20L * 60L * 20L;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final DialogueContext live;
    private final boolean playerOnly;
    private final QuestConditionQueries quests;
    private final QuestDefinition definition;
    private final VillagerQuestSavedData.QuestProgress progress;

    private VillagerConditionAccess(ServerLevel level, ServerPlayer player, DialogueContext live,
            QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, boolean playerOnly, QuestConditionQueries quests) {
        this.level = level;
        this.player = player;
        this.live = live;
        this.playerOnly = playerOnly;
        this.quests = quests;
        this.definition = definition;
        this.progress = progress;
    }

    public static ConditionContext forDialogue(DialogueContext context, QuestConditionQueries quests) {
        return forDialogue(context, null, null, quests);
    }

    public static ConditionContext forDialogue(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, QuestConditionQueries quests) {
        if (context == null) return null;
        return new VillagerConditionAccess(context.level(), context.player(), context.villager() == null ? null : context,
                definition, progress, context.villager() == null, quests).context(null);
    }

    public static ConditionContext forSaved(ServerLevel level, ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return forSaved(level, player, definition, progress, QuestConditionQueries.UNAVAILABLE);
    }

    public static ConditionContext forSaved(ServerLevel level, ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, QuestConditionQueries quests) {
        return new VillagerConditionAccess(level, player, null, definition, progress, false, quests).context(
                progress == null || progress.startedVillagerId() == null ? null : new ConditionContext.ProviderSnapshot(
                        progress.issuerLevel(), progress.issuerDimension(), progress.issuerPos(), progress.issuerVillageKey()));
    }

    public static ConditionContext forPlayer(ServerPlayer player) {
        return forPlayer(player, QuestConditionQueries.UNAVAILABLE);
    }

    public static ConditionContext forPlayer(ServerPlayer player, QuestConditionQueries quests) {
        return new VillagerConditionAccess(player.serverLevel(), player, null, null, null, true, quests).context(null);
    }

    private ConditionContext context(ConditionContext.ProviderSnapshot binding) {
        EnumSet<ConditionCapability> capabilities = EnumSet.of(ConditionCapability.WORLD_KNOWN);
        if (this.player != null) capabilities.add(ConditionCapability.PLAYER_LIVE);
        if (this.live != null) capabilities.add(ConditionCapability.PROVIDER_LIVE);
        if (binding != null) capabilities.add(ConditionCapability.PROVIDER_SNAPSHOT);
        if (binding != null && !binding.villageKey().isBlank()
                || this.live != null && !this.live.villageKey().isBlank()) capabilities.add(ConditionCapability.VILLAGE_KNOWN);
        return new ConditionContext(this.player == null ? null : this.player.getUUID(),
                this.live != null ? this.live.providerId() : this.progress == null ? null : this.progress.startedVillagerId(),
                binding, this.live == null ? null : this.live.villager(),
                this.definition == null ? null : new ConditionContext.QuestRun(this.definition.id(),
                        this.progress == null ? null : this.progress.questRunId()), capabilities, this);
    }

    @Override
    public ConditionOutcome evaluate(DialogueCondition condition, QuestTriggerContext trigger) {
        if (condition instanceof com.jvn.villagerretaliation.dialogue.Knowledge.Condition knowledge)
            return knowledge.evaluate(this.level, this.player, this.live != null ? this.live.providerId()
                    : this.progress == null ? null : this.progress.startedVillagerId());
        if (condition instanceof com.jvn.villagerretaliation.dialogue.ExplorationConditions.Check check)
            return check.reference().evaluate(this.level, this.player, this.live, this.progress);
        if (condition instanceof com.jvn.villagerretaliation.dialogue.ProgressConditions.Check check)
            return check.reference().evaluate(this.level, this.player, this.live, this.progress);
        if (condition instanceof com.jvn.villagerretaliation.dialogue.PlayerStatCondition stat) return stat.evaluate(this.player);
        if (condition instanceof com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Check check)
            return check.reference().evaluate(this.level, this.player);
        if (condition instanceof DialogueCondition.WorkState work && work.reference().check().equals("eligible"))
            return this.live == null ? ConditionOutcome.UNKNOWN : result(com.jvn.villagerretaliation.quest.tutorial.TutorialService.eligible(this.live, work.reference().role()));
        if (condition instanceof DialogueCondition.WorkState work)
            return work.reference().evaluate(this.level, this.player).outcome();
        if (condition instanceof DialogueCondition.Health health) {
            var entity = health.subject().equals("player") ? this.player : this.live == null ? null : this.live.villager();
            return entity == null || entity.isRemoved() ? ConditionOutcome.UNKNOWN
                    : result(com.jvn.villagerretaliation.dialogue.DialogueContextConditions.health(entity, health));
        }
        if (condition instanceof DialogueCondition.PhysicalState state) {
            var entity = state.subject().equals("player") ? this.player : this.live == null ? null : this.live.villager();
            return entity == null || entity.isRemoved() ? ConditionOutcome.UNKNOWN
                    : result(com.jvn.villagerretaliation.dialogue.DialogueContextConditions.physicalState(entity, state));
        }
        if (condition instanceof DialogueCondition.CompanionState companion) {
            return this.live == null || this.player == null || this.live.villager().isRemoved() ? ConditionOutcome.UNKNOWN
                    : result(com.jvn.villagerretaliation.dialogue.DialogueContextConditions.companion(this.live, companion));
        }
        if (condition instanceof DialogueCondition.TriggerPayload payload) {
            return this.live == null && !this.playerOnly && trigger == null ? ConditionOutcome.UNKNOWN
                    : result(payload.matches(this.live, trigger));
        }
        if (condition instanceof DialogueCondition.SelectedChoice choice) return this.quests.selectedChoice(choice);
        if (condition instanceof DialogueCondition.StageHistory history) return this.quests.stageHistory(history);
        if (this.live == null) return savedConditionState(this.player, this.level, this.definition, this.progress, condition, this.quests);
        String unavailable = condition.unavailableReason(this.live);
        if (!unavailable.isBlank()) return ConditionOutcome.UNKNOWN;
        if (condition instanceof DialogueCondition.Quest quest) {
            return this.quests.matchesState(quest.questId(), quest.states());
        }
        return result(condition.matches(this.live, trigger));
    }

    @Override
    public String unavailableReason(DialogueCondition condition, QuestTriggerContext trigger) {
        if (condition instanceof com.jvn.villagerretaliation.dialogue.ExplorationConditions.Check)
            return "The player, settlement identity or selected registry resource is unavailable.";
        if (condition instanceof com.jvn.villagerretaliation.dialogue.ProgressConditions.Check)
            return "The player, provider, settlement or required settlement observations are unavailable.";
        if (condition instanceof com.jvn.villagerretaliation.dialogue.PlayerStatCondition stat) return stat.reason(this.player);
        if (condition instanceof com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Check check)
            return check.reference().reason(this.level, this.player);
        if (condition instanceof DialogueCondition.WorkState work)
            return work.reference().evaluate(this.level, this.player).reason();
        if (condition instanceof DialogueCondition.SelectedChoice || condition instanceof DialogueCondition.StageHistory)
            return "quest history is unavailable";
        if (this.live != null) {
            String reason = condition.unavailableReason(this.live);
            return reason.isBlank() ? "condition cannot be evaluated" : reason;
        }
        if (condition instanceof DialogueCondition.CampaignEvidence evidence) return evidence.reference().evaluate(this.level, this.player).diagnostic();
        if (condition instanceof DialogueCondition.CampaignFact fact) return fact.reference().evaluate(this.level, this.player.getUUID()).diagnostic();
        if (condition instanceof DialogueCondition.CampaignOutcome outcome) return outcome.reference().evaluate(this.level, this.player.getUUID()).diagnostic();
        if (condition instanceof DialogueCondition.CampaignStanding standing) return standing.reference().evaluate(this.level, this.player.getUUID()).diagnostic();
        if (condition instanceof DialogueCondition.CampaignTransaction transaction) return transaction.reference().evaluate(this.level, this.player.getUUID()).diagnostic();
        if (condition instanceof DialogueCondition.CampaignCharacter character) return character.reference().evaluate(this.level, null).diagnostic();
        if (condition instanceof DialogueCondition.CampaignCompanion companion) return companion.reference().evaluate(this.level, this.player).diagnostic();
        if (condition instanceof DialogueCondition.CampaignLocation location) return location.reference().evaluate(this.level, this.player).diagnostic();
        return "condition cannot be evaluated";
    }

    public static boolean hasSelectedChoice(DialogueContext context, DialogueCondition.SelectedChoice condition) {
        return context != null && hasSelectedChoice(context.level(), context.player().getUUID(), condition);
    }

    public static boolean hasSelectedChoice(ServerLevel level, UUID playerId, DialogueCondition.SelectedChoice condition) {
        return choiceHistory(level, playerId, condition.questId()).stream().anyMatch(choice ->
                matchesChoiceValue(condition.scenePath(), choice.scenePath())
                        && matchesChoiceValue(condition.responseId(), choice.responseId())
                        && matchesChoiceValue(condition.priorStage(), choice.priorStage())
                        && matchesChoiceValue(condition.nextStage(), choice.nextStage()));
    }

    public static boolean hasStageHistory(DialogueContext context, DialogueCondition.StageHistory condition) {
        return context != null && hasStageHistory(context.level(), context.player().getUUID(), condition);
    }

    public static boolean hasStageHistory(ServerLevel level, UUID playerId, DialogueCondition.StageHistory condition) {
        return choiceHistory(level, playerId, condition.questId()).stream().anyMatch(choice -> {
            boolean stageMatches = condition.stage().isBlank() || choice.priorStage().equals(condition.stage())
                    || choice.nextStage().equals(condition.stage());
            return stageMatches && matchesChoiceValue(condition.priorStage(), choice.priorStage())
                    && matchesChoiceValue(condition.nextStage(), choice.nextStage());
        });
    }

    private static List<VillagerQuestSavedData.ChoiceHistoryEntry> choiceHistory(ServerLevel level, UUID playerId, ResourceLocation questId) {
        if (level == null || playerId == null || questId == null) return List.of();
        var progress = VillagerQuestSavedData.get(level).get(playerId, questId);
        return progress == null ? List.of() : progress.choiceHistory();
    }

    private static boolean matchesChoiceValue(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.trim().equals(actual);
    }

    private static ConditionOutcome result(boolean matches) {
        return matches ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }

    private static ConditionOutcome savedConditionState(ServerPlayer player, ServerLevel level,
            QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, DialogueCondition condition, QuestConditionQueries quests) {
        if (condition instanceof DialogueCondition.Quest quest) {
            return questConditionStateWithoutLiveContext(player, level, quest.questId(), quest.states(), quests);
        }
        if (condition instanceof DialogueCondition.QuestFact fact) {
            return questFactStateWithoutLiveContext(player, level, definition, progress, fact);
        }
        if (condition instanceof DialogueCondition.CampaignEvidence evidence) {
            var result = evidence.reference().evaluate(level, player);
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignFact fact) {
            var result = fact.reference().evaluate(level, player.getUUID());
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignOutcome outcome) {
            var result = outcome.reference().evaluate(level, player.getUUID());
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignStanding standing) {
            var result = standing.reference().evaluate(level, player.getUUID());
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignTransaction transaction) {
            var result = transaction.reference().evaluate(level, player.getUUID());
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignCharacter character) {
            var result = character.reference().evaluate(level, null);
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignCompanion companion) {
            var result = companion.reference().evaluate(level, player);
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.CampaignLocation location) {
            var result = location.reference().evaluate(level, player);
            return campaignConditionState(result.matches(), result.diagnostic());
        }
        if (condition instanceof DialogueCondition.Reputation reputation) {
            return reputationStateWithoutLiveContext(player, level, progress, reputation);
        }
        if (condition instanceof DialogueCondition.Memory memory) {
            ConditionOutcome memoryState = memoryStateWithoutLiveContext(player, level, progress, memory);
            if (memoryState != ConditionOutcome.UNKNOWN) {
                return memoryState;
            }
        }
        if (condition instanceof DialogueCondition.SocialAttribute socialAttribute) {
            return socialAttributeStateWithoutLiveContext(level, progress, socialAttribute);
        }
        if (condition instanceof DialogueCondition.Skill skill) {
            return skillStateWithoutLiveContext(level, progress, skill);
        }
        if (condition instanceof DialogueCondition.Family family) {
            return familyStateWithoutLiveContext(level, progress, family);
        }
        if (condition instanceof DialogueCondition.Relationship relationship) {
            return relationshipStateWithoutLiveContext(level, progress, relationship);
        }
        if (condition instanceof DialogueCondition.RecruitmentMemory recruitmentMemory) {
            return recruitmentMemoryStateWithoutLiveContext(player, level, progress, recruitmentMemory);
        }
        if (condition instanceof DialogueCondition.VillagerAge villagerAge) {
            return villagerAgeStateWithoutLiveContext(level, progress, villagerAge);
        }
        if (condition instanceof DialogueCondition.VillagerLevel villagerLevel) {
            return villagerLevelStateWithoutLiveContext(progress, villagerLevel);
        }
        if (condition instanceof DialogueCondition.Mood mood) {
            return moodStateWithoutLiveContext(level, progress, mood);
        }
        if (condition instanceof DialogueCondition.PlayerItem item) return result(item.condition().matches(player));
        if (condition instanceof DialogueCondition.Dimension dimension) return result(dimension.dimensions().contains(level.dimension().location()));
        if (condition instanceof DialogueCondition.Biome biome) return result(biome.matches(level, player.blockPosition()));
        if (condition instanceof DialogueCondition.Advancement advancement) return result(advancement.matches(level, player));
        if (condition instanceof DialogueCondition.Scoreboard scoreboard) return result(scoreboard.matches(level, player));
        if (condition instanceof DialogueCondition.NearbyEntity nearby) return result(nearby.matches(level, nearby.aroundPlayer() ? player : null));
        if (condition instanceof DialogueCondition.Weather weather) {
            return weather.states().contains(savedWeatherState(level, player, progress))
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
        }
        if (condition instanceof DialogueCondition.Time time) {
            return time.times().contains(savedTimeOfDay(level)) ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
        }
        return ConditionOutcome.UNKNOWN;
    }

    private static ConditionOutcome campaignConditionState(boolean matches, String diagnostic) {
        if (!diagnostic.isBlank()) {
            return ConditionOutcome.UNKNOWN;
        }
        return matches ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }

    private static ConditionOutcome questConditionStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            ResourceLocation questId,
            Set<String> states,
            QuestConditionQueries quests) {
        QuestDefinition target = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (target == null) {
            return ConditionOutcome.UNMET;
        }
        if (states == null || states.isEmpty()) {
            return ConditionOutcome.MATCHED;
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), questId);
        boolean sawUnknown = false;
        for (String state : states) {
            ConditionOutcome stateMatch = Set.of("ready", "turn_in", "turnin", "completeable", "completable").contains(state)
                    ? quests.ready(target, progress)
                    : savedQuestState(target, progress, state);
            if (stateMatch == ConditionOutcome.MATCHED) {
                return ConditionOutcome.MATCHED;
            }
            sawUnknown |= stateMatch == ConditionOutcome.UNKNOWN;
        }
        return sawUnknown ? ConditionOutcome.UNKNOWN : ConditionOutcome.UNMET;
    }

    private static ConditionOutcome savedQuestState(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean completed = progress != null && progress.completionCount() > 0;
        return switch (normalized) {
            case "not_started", "locked" -> progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "active", "started" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "completed", "complete" -> {
                if (completed) {
                    yield ConditionOutcome.MATCHED;
                }
                yield QuestAvailabilityService.isPlayerCompletionScope(definition.rules().completionScope())
                        ? ConditionOutcome.UNMET
                        : ConditionOutcome.UNKNOWN;
            }
            case "abandoned", "dropped" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "failed", "failure" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.FAILED
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "expired", "timed_out", "time_out" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.EXPIRED
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "consumed", "removed", "removed_forever" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "branch_locked", "branch_blocked", "blocked_branch" -> (progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED
                    && "branch_lock".equals(progress.consumedReason()))
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case "not_completed" -> {
                if (completed) {
                    yield ConditionOutcome.UNMET;
                }
                yield QuestAvailabilityService.isPlayerCompletionScope(definition.rules().completionScope())
                        ? ConditionOutcome.MATCHED
                        : ConditionOutcome.UNKNOWN;
            }
            default -> ConditionOutcome.UNKNOWN;
        };
    }

    private static ConditionOutcome questFactStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.QuestFact fact) {
        String scopeKey = savedQuestFactScopeKey(player, level, definition, progress, fact.scope(), fact.questId());
        if (scopeKey.isBlank()) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        if (!fact.tags().isEmpty() && fact.tags().stream().noneMatch(tag -> facts.hasTag(scopeKey, tag))) {
            return ConditionOutcome.UNMET;
        }
        if (fact.key() == null || fact.key().isBlank()) {
            return fact.tags().isEmpty() ? ConditionOutcome.UNKNOWN : ConditionOutcome.MATCHED;
        }
        Optional<String> variable = facts.variable(scopeKey, fact.key());
        if (!fact.values().isEmpty() && variable.stream().noneMatch(fact.values()::contains)) {
            return ConditionOutcome.UNMET;
        }
        int counter = facts.counter(scopeKey, fact.key());
        if (fact.min() != null && counter < fact.min()) {
            return ConditionOutcome.UNMET;
        }
        if (fact.max() != null && counter > fact.max()) {
            return ConditionOutcome.UNMET;
        }
        return !fact.values().isEmpty() || fact.min() != null || fact.max() != null || variable.isPresent() || counter != 0
                ? ConditionOutcome.MATCHED
                : ConditionOutcome.UNMET;
    }

    private static ConditionOutcome reputationStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Reputation reputation) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerReputationSavedData.ReputationEntry entry =
                VillagerReputationSavedData.get(level).get(villagerId, player.getUUID());
        int value = entry == null ? 0 : entry.reputation();
        VillagerReputationLevel reputationLevel = VillagerReputationLevel.fromReputation(value);
        if (!reputation.levels().isEmpty() && !reputation.levels().contains(reputationLevel)) {
            return ConditionOutcome.UNMET;
        }
        if (reputation.minReputation() != null && value < reputation.minReputation()) {
            return ConditionOutcome.UNMET;
        }
        if (reputation.maxReputation() != null && value > reputation.maxReputation()) {
            return ConditionOutcome.UNMET;
        }
        return ConditionOutcome.MATCHED;
    }

    private static ConditionOutcome socialAttributeStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.SocialAttribute socialAttribute) {
        Optional<VillagerProfile> profile = savedIssuerProfile(level, progress);
        if (profile.isEmpty()) {
            return ConditionOutcome.UNKNOWN;
        }
        for (var attribute : socialAttribute.attributes()) {
            int value = profile.get().socialAttributes().get(attribute);
            if (socialAttribute.minValue() != null && value < socialAttribute.minValue()) {
                continue;
            }
            if (socialAttribute.maxValue() != null && value > socialAttribute.maxValue()) {
                continue;
            }
            return ConditionOutcome.MATCHED;
        }
        return ConditionOutcome.UNMET;
    }

    private static ConditionOutcome skillStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Skill skill) {
        Optional<VillagerProfile> profile = savedIssuerProfile(level, progress);
        if (profile.isEmpty()) {
            return ConditionOutcome.UNKNOWN;
        }
        for (var villagerSkill : skill.skills()) {
            int value = profile.get().skills().get(villagerSkill);
            if (skill.minValue() != null && value < skill.minValue()) {
                continue;
            }
            if (skill.maxValue() != null && value > skill.maxValue()) {
                continue;
            }
            if (skill.minRank() != null && value < skill.minRank().minInclusive()) {
                continue;
            }
            if (skill.maxRank() != null && value > skill.maxRank().maxInclusive()) {
                continue;
            }
            return ConditionOutcome.MATCHED;
        }
        return ConditionOutcome.UNMET;
    }

    private static Optional<VillagerProfile> savedIssuerProfile(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        return villagerId == null ? Optional.empty() : VillagerProfileManager.getProfile(level, villagerId);
    }

    private static ConditionOutcome familyStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Family family) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerFamilyTreeSnapshot snapshot = VillagerSocialGraphService.familySnapshot(level, villagerId);
        if (family.relations().isEmpty()) {
            return snapshot.hasFamily() ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
        }
        for (String relation : family.relations()) {
            if (savedFamilyRelationMatches(snapshot, relation)) {
                return ConditionOutcome.MATCHED;
            }
        }
        return ConditionOutcome.UNMET;
    }

    private static boolean savedFamilyRelationMatches(VillagerFamilyTreeSnapshot snapshot, String relation) {
        return switch (relation) {
            case "family", "any" -> snapshot.hasFamily();
            case "parent" -> snapshot.hasParent();
            case "sibling" -> snapshot.hasSibling();
            case "spouse" -> snapshot.hasSpouse();
            case "child" -> snapshot.hasChild();
            case "grandparent" -> snapshot.hasGrandparent();
            case "grandchild" -> snapshot.hasGrandchild();
            case "descendant" -> snapshot.hasDescendant();
            case "aunt_uncle", "aunt_or_uncle" -> snapshot.hasAuntUncle();
            case "cousin" -> snapshot.hasCousin();
            case "niece_nephew", "niece_or_nephew" -> snapshot.hasNieceNephew();
            case "extended_family" -> snapshot.hasExtendedFamily();
            case "deceased_family" -> snapshot.hasDeceasedFamily();
            default -> false;
        };
    }

    private static ConditionOutcome relationshipStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Relationship relationship) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerRelationshipSnapshot snapshot = VillagerSocialGraphService.relationshipSnapshot(level, villagerId);
        if (relationship.states().isEmpty()) {
            return snapshot.hasRelationships() ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
        }
        for (String state : relationship.states()) {
            if (savedRelationshipStateMatches(snapshot, state)) {
                return ConditionOutcome.MATCHED;
            }
        }
        return ConditionOutcome.UNMET;
    }

    private static boolean savedRelationshipStateMatches(VillagerRelationshipSnapshot snapshot, String state) {
        return switch (state) {
            case "relationship", "any" -> snapshot.hasRelationships();
            case "current", "current_relationship" -> snapshot.hasCurrentRelationship();
            case "past", "past_relationship" -> snapshot.hasPastRelationship();
            case "crush" -> snapshot.hasCrush();
            case "dating", "dating_partner" -> snapshot.hasDatingPartner();
            case "fiance", "fiancee" -> snapshot.hasFiance();
            case "romantic_spouse", "spouse" -> snapshot.hasRomanticSpouse();
            case "separated", "separated_partner" -> snapshot.hasSeparatedPartner();
            case "widowed", "widowed_partner" -> snapshot.hasWidowedPartner();
            default -> false;
        };
    }

    private static ConditionOutcome recruitmentMemoryStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.RecruitmentMemory recruitmentMemory) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerInteractionTracker.RecruitmentMemory savedMemory =
                savedRecruitmentMemory(player, level, villagerId).orElse(null);
        if (savedMemory == null) {
            return ConditionOutcome.UNMET;
        }
        if (!recruitmentMemory.scenarios().isEmpty()
                && recruitmentMemory.scenarios().stream().noneMatch(scenario -> savedRecruitmentScenarioMatches(savedMemory, scenario))) {
            return ConditionOutcome.UNMET;
        }
        if (!recruitmentMemory.biomeKeys().isEmpty()
                && !recruitmentMemory.biomeKeys().contains(savedRecruitmentBiomeKey(savedMemory))) {
            return ConditionOutcome.UNMET;
        }
        if (recruitmentMemory.minFollowDistance() != null
                && savedMemory.distanceBlocks() < recruitmentMemory.minFollowDistance()) {
            return ConditionOutcome.UNMET;
        }
        if (recruitmentMemory.boatTrip() != null
                && savedMemory.boatTrip() != recruitmentMemory.boatTrip()) {
            return ConditionOutcome.UNMET;
        }
        if (recruitmentMemory.oceanCrossing() != null
                && savedMemory.oceanCrossing() != recruitmentMemory.oceanCrossing()) {
            return ConditionOutcome.UNMET;
        }
        boolean swimTrip = savedMemory.oceanCrossing() && !savedMemory.boatTrip();
        if (recruitmentMemory.swimTrip() != null && swimTrip != recruitmentMemory.swimTrip()) {
            return ConditionOutcome.UNMET;
        }
        if (recruitmentMemory.excludesOceanCrossing() != null
                && recruitmentMemory.excludesOceanCrossing()
                && savedMemory.oceanCrossing()) {
            return ConditionOutcome.UNMET;
        }
        return ConditionOutcome.MATCHED;
    }

    private static ConditionOutcome memoryStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Memory memory) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerInteractionSavedData interactionData = VillagerInteractionSavedData.get(level);
        return switch (memory.kind()) {
            case RECENT_BROKEN_BED -> interactionData.hasRecentBrokenBedMemory(
                    villagerId,
                    player.getUUID(),
                    level.getGameTime(),
                    BROKEN_BED_MEMORY_TICKS)
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case RECENT_DIRECT_HIT -> interactionData.hasRecentDirectHitMemory(
                    villagerId,
                    player.getUUID(),
                    level.getGameTime(),
                    DIRECT_HIT_MEMORY_TICKS)
                    ? ConditionOutcome.MATCHED
                    : ConditionOutcome.UNMET;
            case GEAR_REPORT_USED_IN_COMBAT -> {
                VillagerInteractionTracker.GearReport gearReport =
                        interactionData.unreportedGearReport(villagerId, player.getUUID());
                yield gearReport != null && gearReport.usedInCombat()
                        ? ConditionOutcome.MATCHED
                        : ConditionOutcome.UNMET;
            }
            case GEAR_REPORT_UNUSED_IN_COMBAT -> {
                VillagerInteractionTracker.GearReport gearReport =
                        interactionData.unreportedGearReport(villagerId, player.getUUID());
                yield gearReport != null && !gearReport.usedInCombat()
                        ? ConditionOutcome.MATCHED
                        : ConditionOutcome.UNMET;
            }
            case RECRUITMENT_MEMORY -> recruitmentMemoryPresentWithoutLiveContext(player, level, progress);
            case EVENT_TAG -> eventTagMemoryStateWithoutLiveContext(player, level, progress, memory, villagerId);
        };
    }

    private static ConditionOutcome eventTagMemoryStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel fallbackLevel,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Memory memory,
            UUID villagerId) {
        if (memory.tags().isEmpty()) {
            return ConditionOutcome.UNKNOWN;
        }
        ServerLevel memoryLevel = savedVillageMemoryLevel(fallbackLevel, progress);
        if (memoryLevel == null) {
            return ConditionOutcome.UNKNOWN;
        }
        UUID playerId = player.getUUID();
        if (memory.source() != DialogueCondition.MemorySource.OTHER_VILLAGER
                && hasMatchingOfflineMemory(
                        VillageEventMemory.recentForVillager(memoryLevel, villagerId),
                        memory,
                        playerId,
                        villagerId,
                        false)) {
            return ConditionOutcome.MATCHED;
        }
        if (memory.source() == DialogueCondition.MemorySource.THIS_VILLAGER) {
            return ConditionOutcome.UNMET;
        }
        var rosterVillage = VillageEventMemory.villageForVillager(memoryLevel, villagerId);
        Optional<BlockPos> savedVillagePos = savedVillageMemoryQueryPos(progress);
        List<VillageEventMemory.MemoryEvent> communalEvents = rosterVillage
                .map(id -> VillageEventMemory.recentForVillage(memoryLevel, id))
                .orElseGet(() -> savedVillagePos
                        .map(pos -> VillageEventMemory.recentForVillageAt(memoryLevel, pos))
                        .orElseGet(List::of));
        if (rosterVillage.isEmpty() && savedVillagePos.isEmpty()) {
            return memory.source() == DialogueCondition.MemorySource.ANY
                    ? ConditionOutcome.UNMET
                    : ConditionOutcome.UNKNOWN;
        }
        return hasMatchingOfflineMemory(
                communalEvents,
                memory,
                playerId,
                villagerId,
                true)
                ? ConditionOutcome.MATCHED
                : ConditionOutcome.UNMET;
    }

    private static boolean hasMatchingOfflineMemory(
            List<VillageEventMemory.MemoryEvent> events,
            DialogueCondition.Memory memory,
            UUID playerId,
            UUID villagerId,
            boolean communal) {
        for (VillageEventMemory.MemoryEvent event : events) {
            if (!memory.tags().contains(event.tagId())) {
                continue;
            }
            if (memory.currentPlayerOnly() && !playerId.equals(event.playerId())) {
                continue;
            }
            if (communal && villagerId.equals(event.sourceId())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static Optional<BlockPos> savedVillageMemoryQueryPos(VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            return Optional.empty();
        }
        Optional<BlockPos> villagePos = VillageScopeKeys.pos(progress.issuerVillageKey());
        return villagePos.isPresent() ? villagePos : Optional.ofNullable(progress.issuerPos());
    }

    private static ServerLevel savedVillageMemoryLevel(
            ServerLevel fallbackLevel,
            VillagerQuestSavedData.QuestProgress progress) {
        ResourceKey<Level> dimension = progress == null
                ? null
                : VillageScopeKeys.dimension(progress.issuerVillageKey()).orElse(progress.issuerDimension());
        if (dimension == null || dimension.equals(fallbackLevel.dimension())) {
            return fallbackLevel;
        }
        return fallbackLevel.getServer().getLevel(dimension);
    }

    private static ConditionOutcome recruitmentMemoryPresentWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        return savedRecruitmentMemory(player, level, villagerId).isPresent()
                ? ConditionOutcome.MATCHED
                : ConditionOutcome.UNMET;
    }

    private static Optional<VillagerInteractionTracker.RecruitmentMemory> savedRecruitmentMemory(
            ServerPlayer player,
            ServerLevel level,
            UUID villagerId) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .recruitmentMemory(villagerId, player.getUUID()));
    }

    private static boolean savedRecruitmentScenarioMatches(
            VillagerInteractionTracker.RecruitmentMemory memory,
            String scenario) {
        return memory.scenario() != null && scenario != null && memory.scenario().equalsIgnoreCase(scenario);
    }

    private static String savedRecruitmentBiomeKey(VillagerInteractionTracker.RecruitmentMemory memory) {
        if (memory.biomeName() == null || memory.biomeName().isBlank()) {
            return "";
        }
        return memory.biomeName().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static ConditionOutcome villagerAgeStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.VillagerAge villagerAge) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        Optional<Boolean> savedBaby = VillagerSocialGraphService.knownBaby(level, villagerId);
        if (savedBaby.isEmpty()) {
            return ConditionOutcome.UNKNOWN;
        }
        boolean isBaby = savedBaby.get();
        if (villagerAge.baby() != null && isBaby != villagerAge.baby()) {
            return ConditionOutcome.UNMET;
        }
        if (villagerAge.adult() != null && isBaby == villagerAge.adult()) {
            return ConditionOutcome.UNMET;
        }
        return ConditionOutcome.MATCHED;
    }

    private static ConditionOutcome villagerLevelStateWithoutLiveContext(
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.VillagerLevel villagerLevel) {
        int level = progress == null ? 0 : progress.issuerLevel();
        if (level <= 0) {
            return ConditionOutcome.UNKNOWN;
        }
        if (!villagerLevel.levels().isEmpty() && !villagerLevel.levels().contains(level)) {
            return ConditionOutcome.UNMET;
        }
        if (villagerLevel.minLevel() != null && level < villagerLevel.minLevel()) {
            return ConditionOutcome.UNMET;
        }
        if (villagerLevel.maxLevel() != null && level > villagerLevel.maxLevel()) {
            return ConditionOutcome.UNMET;
        }
        return ConditionOutcome.MATCHED;
    }

    private static ConditionOutcome moodStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Mood mood) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionOutcome.UNKNOWN;
        }
        VillagerMoodState state = VillagerRetaliationConfig.ENABLE_VILLAGER_MOODS.get()
                ? VillagerMoodSavedData.get(level).get(villagerId).withEffectiveDecay(level.getGameTime())
                : VillagerMoodState.DEFAULT;
        if (!mood.moods().isEmpty() && !mood.moods().contains(state.primaryMood())) {
            return ConditionOutcome.UNMET;
        }
        if (mood.minIntensity() != null && state.intensity() < mood.minIntensity()) {
            return ConditionOutcome.UNMET;
        }
        if (mood.maxIntensity() != null && state.intensity() > mood.maxIntensity()) {
            return ConditionOutcome.UNMET;
        }
        return ConditionOutcome.MATCHED;
    }

    private static String savedQuestFactScopeKey(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestFactScope scope,
            ResourceLocation questId) {
        QuestFactScope resolvedScope = scope == null ? QuestFactScope.PLAYER : scope;
        ResourceLocation resolvedQuestId = questId == null && definition != null ? definition.id() : questId;
        QuestScopeKey scopeKey = switch (resolvedScope) {
            case PLAYER -> QuestScopeKey.player(player.getUUID());
            case WORLD -> QuestScopeKey.WORLD;
            case QUEST -> QuestScopeKey.quest(player.getUUID(), resolvedQuestId);
            case VILLAGER -> QuestScopeKey.villager(progress == null ? null : progress.startedVillagerId());
            case VILLAGE -> QuestScopeKey.village(progress == null ? "" : factVillageScopeKey(level, progress));
        };
        return scopeKey.asString();
    }

    private static DialogueContext.WeatherState savedWeatherState(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        if (level.isThundering()) {
            return DialogueContext.WeatherState.THUNDER;
        }
        BlockPos pos = progress == null || progress.issuerPos() == null ? player.blockPosition() : progress.issuerPos();
        return level.isRainingAt(pos) ? DialogueContext.WeatherState.RAIN : DialogueContext.WeatherState.CLEAR;
    }

    private static DialogueContext.TimeOfDay savedTimeOfDay(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 6000L) {
            return DialogueContext.TimeOfDay.MORNING;
        }
        if (dayTime < 12000L) {
            return DialogueContext.TimeOfDay.AFTERNOON;
        }
        if (dayTime < 14000L) {
            return DialogueContext.TimeOfDay.EVENING;
        }
        return DialogueContext.TimeOfDay.NIGHT;
    }

    private static String factVillageScopeKey(ServerLevel level, VillagerQuestSavedData.QuestProgress progress) {
        if (!progress.issuerVillageKey().isBlank()) return progress.issuerVillageKey();
        var entity = progress.startedVillagerId() == null ? null : level.getEntity(progress.startedVillagerId());
        if (entity instanceof net.minecraft.world.entity.npc.Villager villager && villager.isAlive()) {
            return VillageScopeKeys.forVillager(level, villager);
        }
        if (progress.issuerPos() == null) return "";
        return VillageScopeKeys.forPosition(progress.issuerDimension() == null ? level.dimension() : progress.issuerDimension(), progress.issuerPos());
    }
}
