package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;

public final class QuestRewardService {
    private static final Set<VillagerQuestSavedData.QuestProgress> DELIVERING = new HashSet<>();

    private QuestRewardService() {
    }

    public static String unresolvedDiagnostic(MinecraftServer server, QuestDefinition definition) {
        ResourceLocation rewardId = definition == null ? null : definition.rewards().lootTable();
        if (rewardId == null) return "";
        QuestRewardResolver.Resolution resolution = QuestRewardResolver.resolve(server, rewardId);
        return resolution.resolved() ? "" : resolution.diagnostic();
    }

    public static ActionResult award(DialogueContext context, QuestDefinition definition, Map<String, String> replacements) {
        var progress = VillagerQuestSavedData.get(context.level()).getOrCreate(context.player().getUUID(), definition.id());
        return award(context, definition, progress, replacements);
    }

    public static ActionResult award(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, Map<String, String> replacements) {
        if (!progress.rewardDeliveryIssue().isBlank()) {
            return ActionResult.unavailable(progress.rewardDeliveryIssue(), Set.of(), VillagerActionResult.EMPTY);
        }
        if (!DELIVERING.add(progress)) {
            return ActionResult.unavailable("Quest reward delivery is already in progress.", Set.of(), VillagerActionResult.EMPTY);
        }
        try {
            var data = VillagerQuestSavedData.get(context.level());
            var delivery = progress.rewardDelivery();
            if (delivery == null) {
                delivery = new QuestRewardDelivery(definition.rewards(), 0);
                progress.recordRewardDelivery(delivery);
                data.setDirty();
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
                        : action.kind() == VillagerActionDefinition.Kind.MEMORY ? remember(context, action)
                        : VillagerActionRegistry.execute(context, action, replacements);
                QuestDebugTraceService.recordIfEnabled(context.player(), QuestDebugTraceService.EventType.REWARD, definition.id(),
                        "reward type=" + VillagerActionRegistry.canonicalTypeId(action) + " result=" + outcome.status());
                if (!outcome.success()) return outcome;
                progress.recordRewardDelivery(new QuestRewardDelivery(rewards, index + 1));
                data.setDirty();
            }
            context.player().playSound(SoundEvents.PLAYER_LEVELUP, 0.55F, 1.1F);
            return ActionResult.success(VillagerActionResult.success(), Set.of());
        } finally {
            DELIVERING.remove(progress);
        }
    }

    private static ActionResult remember(DialogueContext context, VillagerActionDefinition action) {
        if (context.villager() == null) return ActionResult.unavailable("This memory reward requires a villager provider.", Set.of(), VillagerActionResult.EMPTY);
        var registry = VillageAllegianceRegistrySavedData.get(context.level());
        boolean bucketAvailable = action.memoryScope().includesVillager()
                || registry.resolveAt(context.level(), context.villager().blockPosition()).flatMap(registry::canonical).isPresent();
        if (!bucketAvailable) {
            return ActionResult.unavailable("The quest's village memory is unavailable.", Set.of(), VillagerActionResult.EMPTY);
        }
        var result = VillageEventMemory.remember(context.level(), action.memoryTag(), context.villager().blockPosition(),
                context.villager(), context.player(), action.memoryScope());
        return result.changed() ? ActionResult.success(VillagerActionResult.success(), Set.of())
                : ActionResult.alreadySatisfied("memory reward already recorded", Set.of(), VillagerActionResult.EMPTY);
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

}
