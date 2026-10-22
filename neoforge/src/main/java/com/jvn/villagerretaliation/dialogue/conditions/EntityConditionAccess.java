package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionCapability;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueContextConditions;
import com.jvn.villagerretaliation.dialogue.DialogueContext.TimeOfDay;
import com.jvn.villagerretaliation.dialogue.DialogueContext.WeatherState;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record EntityConditionAccess(ServerLevel level, ServerPlayer player, Entity provider) implements ConditionLeafAccess {
    public static ConditionContext forDialogue(DialogueRuntimeContext context) {
        var common = context(context.level(), context.player(), context.speaker(), context.providerId(),
                context.groupKey(), null, null);
        return new ConditionContext(common.playerId(), common.providerId(), common.savedProvider(), common.liveProvider(),
                common.questRun(), common.capabilities(), (condition, trigger) -> {
                    if (condition instanceof DialogueCondition.ReputationTier reputation) {
                        var tier = context.actions().reputationTier(context);
                        return tier.isEmpty() ? ConditionOutcome.UNKNOWN : result(reputation.tiers().contains(tier.orElseThrow()));
                    }
                    if (condition instanceof DialogueCondition.Reputation reputation) {
                        var score = context.actions().reputation(context);
                        if (score.isEmpty() || !reputation.levels().isEmpty()) return ConditionOutcome.UNKNOWN;
                        return result((reputation.minReputation() == null || score.getAsInt() >= reputation.minReputation())
                                && (reputation.maxReputation() == null || score.getAsInt() <= reputation.maxReputation()));
                    }
                    if (condition instanceof DialogueCondition.QuestFact fact) {
                        var facts = context.actions().facts();
                        return facts.isEmpty() ? ConditionOutcome.UNKNOWN
                                : result(FactConditions.matches(fact, facts.orElseThrow(), fact.scope().scopeKey(context, fact.questId())));
                    }
                    if (condition instanceof DialogueCondition.Memory memory) {
                        var access = context.actions().memories();
                        if (access.isEmpty() || memory.kind() != DialogueCondition.MemoryKind.EVENT_TAG) return ConditionOutcome.UNKNOWN;
                        var store = access.orElseThrow();
                        return result(MemoryConditions.matches(memory, context.player().getUUID(), context.providerId(),
                                context.providerId() == null ? java.util.List.of() : store.personal(context.providerId()),
                                context.groupKey().isBlank() ? java.util.List.of() : store.group(context.groupKey())));
                    }
                    return common.queries().evaluate(condition, trigger);
                });
    }

    public static ConditionContext forQuest(QuestExecutionContext context, com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress) {
        var binding = context.binding();
        var snapshot = binding == null ? null : new ConditionContext.ProviderSnapshot(
                binding.level(), binding.dimension(), binding.pos(), binding.villageKey());
        var run = context.quest() == null ? null : new ConditionContext.QuestRun(context.quest().id(),
                progress == null ? null : progress.questRunId());
        return context(context.level(), context.player(), context.liveProvider(),
                binding == null ? null : binding.providerId(), context.villageKey(), snapshot, run);
    }

    private static ConditionContext context(ServerLevel level, ServerPlayer player, Entity provider,
            UUID providerId, String groupKey, ConditionContext.ProviderSnapshot snapshot, ConditionContext.QuestRun run) {
        var capabilities = EnumSet.noneOf(ConditionCapability.class);
        if (level != null) capabilities.add(ConditionCapability.WORLD_KNOWN);
        if (player != null) capabilities.add(ConditionCapability.PLAYER_LIVE);
        if (provider != null && !provider.isRemoved()) capabilities.add(ConditionCapability.PROVIDER_LIVE);
        if (snapshot != null) capabilities.add(ConditionCapability.PROVIDER_SNAPSHOT);
        if (groupKey != null && !groupKey.isBlank()) capabilities.add(ConditionCapability.VILLAGE_KNOWN);
        return new ConditionContext(player == null ? null : player.getUUID(), providerId, snapshot, provider,
                run, capabilities, new EntityConditionAccess(level, player, provider));
    }

    @Override
    public ConditionOutcome evaluate(DialogueCondition condition, QuestTriggerContext trigger) {
        if (condition instanceof com.jvn.villagerretaliation.dialogue.SpeakingStyle.Condition style)
            return provider == null ? ConditionOutcome.UNKNOWN : result(style.style().equals(
                    com.jvn.villagerretaliation.dialogue.SpeakingStyle.forSpeaker(provider.getUUID())));
        if (condition instanceof DialogueCondition.Health health) {
            LivingEntity entity = subject(health.subject());
            return entity == null ? ConditionOutcome.UNKNOWN : result(DialogueContextConditions.health(entity, health));
        }
        if (condition instanceof DialogueCondition.PhysicalState state) {
            LivingEntity entity = subject(state.subject());
            return entity == null ? ConditionOutcome.UNKNOWN : result(DialogueContextConditions.physicalState(entity, state));
        }
        if (condition instanceof DialogueCondition.Dimension dimension) {
            return level == null ? ConditionOutcome.UNKNOWN : result(dimension.dimensions().contains(level.dimension().location()));
        }
        if (condition instanceof DialogueCondition.PlayerItem item) {
            return player == null ? ConditionOutcome.UNKNOWN : result(item.condition().matches(player));
        }
        if (condition instanceof DialogueCondition.Biome biome) {
            Entity origin = provider == null ? player : provider;
            return level == null || origin == null ? ConditionOutcome.UNKNOWN : result(biome.matches(level, origin.blockPosition()));
        }
        if (condition instanceof DialogueCondition.Advancement advancement) {
            return level == null || player == null ? ConditionOutcome.UNKNOWN : result(advancement.matches(level, player));
        }
        if (condition instanceof DialogueCondition.Scoreboard scoreboard) {
            return level == null || player == null ? ConditionOutcome.UNKNOWN : result(scoreboard.matches(level, player));
        }
        if (condition instanceof DialogueCondition.NearbyEntity nearby) {
            Entity origin = nearby.aroundPlayer() ? player : provider;
            return level == null || origin == null ? ConditionOutcome.UNKNOWN : result(nearby.matches(level, origin));
        }
        if (condition instanceof DialogueCondition.VillagerAge age) {
            var living = subject("speaker");
            return living == null ? ConditionOutcome.UNKNOWN : result((age.baby() == null || age.baby() == living.isBaby())
                    && (age.adult() == null || age.adult() != living.isBaby()));
        }
        if (condition instanceof DialogueCondition.Weather weather) {
            if (level == null) return ConditionOutcome.UNKNOWN;
            var state = level.isThundering() ? WeatherState.THUNDER
                    : level.isRaining() ? WeatherState.RAIN
                    : WeatherState.CLEAR;
            return result(weather.states().contains(state));
        }
        if (condition instanceof DialogueCondition.Time time) {
            if (level == null) return ConditionOutcome.UNKNOWN;
            long day = Math.floorMod(level.getDayTime(), 24000L);
            var state = day < 6000 ? TimeOfDay.MORNING
                    : day < 12000 ? TimeOfDay.AFTERNOON
                    : day < 14000 ? TimeOfDay.EVENING
                    : TimeOfDay.NIGHT;
            return result(time.times().contains(state));
        }
        if (condition instanceof DialogueCondition.TriggerPayload payload) {
            return trigger == null ? ConditionOutcome.UNKNOWN : result(payload.matches(null, trigger));
        }
        return ConditionOutcome.UNKNOWN;
    }

    private LivingEntity subject(String subject) {
        var entity = "player".equals(subject) ? player : "speaker".equals(subject) ? provider : null;
        return entity instanceof LivingEntity living && !living.isRemoved() ? living : null;
    }

    private static ConditionOutcome result(boolean matched) {
        return matched ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
    }

    public static ConditionContext forQuest(QuestExecutionContext context, com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        return forQuest(context, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress);
    }

}
