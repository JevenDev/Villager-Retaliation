package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionSequenceRunner;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;

public final class QuestRewardService {
    private QuestRewardService() {
    }

    public static String unresolvedDiagnostic(MinecraftServer server, QuestDefinition definition) {
        ResourceLocation rewardId = definition == null ? null : definition.rewards().lootTable();
        if (rewardId == null) return "";
        QuestRewardResolver.Resolution resolution = QuestRewardResolver.resolve(server, rewardId);
        return resolution.resolved() ? "" : resolution.diagnostic();
    }

    public static ActionResult award(DialogueContext context, QuestDefinition definition,
            Map<String, String> replacements) {
        QuestDefinition.Rewards rewards = definition.rewards();
        List<VillagerActionDefinition> actions = List.of(
                rewardAction(VillagerActionDefinition.Kind.EXPERIENCE, rewards.experience(), null, null),
                rewardAction(VillagerActionDefinition.Kind.REPUTATION, rewards.reputation(), null, null),
                rewardAction(VillagerActionDefinition.Kind.GOSSIP, rewards.gossipReputation(), null, null),
                rewardAction(VillagerActionDefinition.Kind.MEMORY, 0, rewards.memoryEvent(), null, rewards.memoryScope()),
                rewardAction(VillagerActionDefinition.Kind.LOOT, 0, null, rewards.lootTable()));
        ActionResult result = ActionSequenceRunner.run(context, actions, 0, replacements, null,
                (next, outcome) -> QuestDebugTraceService.recordIfEnabled(context.player(),
                        QuestDebugTraceService.EventType.REWARD, definition.id(),
                        "reward type=" + VillagerActionRegistry.canonicalTypeId(actions.get(next - 1))
                                + " result=" + (outcome.legacyResult().ran() ? "success" : "skipped"))).outcome();
        context.villager().playSound(SoundEvents.PLAYER_LEVELUP, 0.55F, 1.1F);
        return result;
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
