package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.resources.ResourceLocation;

public final class QuestRewardDeliveryService {
    private QuestRewardDeliveryService() { }

    public static ActionResult deliver(QuestProgressModel progress, QuestDefinition.Rewards rewardsToDeliver,
            Map<String, String> replacements,
            BiFunction<VillagerActionDefinition, Map<String, String>, ActionResult> executor,
            BiConsumer<VillagerActionDefinition, ActionResult> observer, Runnable checkpoint) {
        if (!progress.rewardDeliveryIssue().isBlank()) {
            return ActionResult.unavailable(progress.rewardDeliveryIssue(), Set.of(), VillagerActionResult.EMPTY);
        }
        var delivery = progress.rewardDelivery();
        if (delivery == null) {
            delivery = new QuestRewardDelivery(rewardsToDeliver, 0);
            progress.recordRewardDelivery(delivery);
            checkpoint.run();
        }
        QuestDefinition.Rewards rewards = delivery.rewards();
        List<VillagerActionDefinition> actions = List.of(
                rewardAction(VillagerActionDefinition.Kind.EXPERIENCE, rewards.experience(), null, null),
                rewardAction(VillagerActionDefinition.Kind.REPUTATION, rewards.reputation(), null, null),
                rewardAction(VillagerActionDefinition.Kind.GOSSIP, rewards.gossipReputation(), null, null),
                rewardAction(VillagerActionDefinition.Kind.MEMORY, 0, rewards.memoryEvent(), null, rewards.memoryScope()),
                rewardAction(VillagerActionDefinition.Kind.LOOT, 0, null, rewards.lootTable()));
        for (int index = delivery.nextAction(); index < actions.size(); index++) {
            var action = actions.get(index);
            boolean empty = switch (action.kind()) {
                case EXPERIENCE -> action.amount() <= 0;
                case REPUTATION, GOSSIP -> action.amount() == 0;
                case MEMORY -> action.memoryTag() == null;
                case LOOT -> action.lootTable() == null;
                default -> false;
            };
            ActionResult outcome = empty ? ActionResult.alreadySatisfied("empty reward", Set.of(), VillagerActionResult.EMPTY)
                    : executor.apply(action, replacements);
            observer.accept(action, outcome);
            if (!outcome.success()) return outcome;
            progress.recordRewardDelivery(new QuestRewardDelivery(rewards, index + 1));
            checkpoint.run();
        }
        return ActionResult.success(VillagerActionResult.success(), Set.of());
    }

    private static VillagerActionDefinition rewardAction(
            VillagerActionDefinition.Kind kind,
            int amount,
            ResourceLocation memoryTag,
            ResourceLocation lootTable) {
        return rewardAction(kind, amount, memoryTag, lootTable, VillageEventMemory.MemoryScope.BOTH);
    }

    private static VillagerActionDefinition rewardAction(
            VillagerActionDefinition.Kind kind,
            int amount,
            ResourceLocation memoryTag,
            ResourceLocation lootTable,
            VillageEventMemory.MemoryScope memoryScope) {
        return new VillagerActionDefinition(
                kind,
                null,
                VillagerActionDefinition.QuestAction.NONE,
                amount,
                memoryTag,
                memoryScope,
                lootTable,
                "",
                "",
                "",
                false,
                QuestFactScope.PLAYER,
                null,
                "",
                "",
                Map.of(),
                Map.of(),
                CompiledQuestTransition.EMPTY,
                null,
                "",
                false,
                false);
    }

    public static ActionResult deliver(QuestProgressState progress, QuestDefinition.Rewards rewardsToDeliver,
            Map<String, String> replacements,
            BiFunction<VillagerActionDefinition, Map<String, String>, ActionResult> executor,
            BiConsumer<VillagerActionDefinition, ActionResult> observer, Runnable checkpoint) {
        return deliver((QuestProgressModel) progress, rewardsToDeliver, replacements, executor, observer, checkpoint);
    }

}
