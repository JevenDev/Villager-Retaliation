package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionCapability;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueContextConditions;
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

    public static ConditionContext forQuest(QuestExecutionContext context, com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
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
}
