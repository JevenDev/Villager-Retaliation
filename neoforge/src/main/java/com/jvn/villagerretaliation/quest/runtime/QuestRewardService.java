package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.HashSet;
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
            ActionResult outcome = QuestRewardDeliveryService.deliver(progress, definition.rewards(), replacements,
                    (action, inherited) -> action.kind() == VillagerActionDefinition.Kind.MEMORY
                            ? remember(context, action) : VillagerActionRegistry.execute(context, action, inherited),
                    (action, result) -> QuestDebugTraceService.recordIfEnabled(context.player(), QuestDebugTraceService.EventType.REWARD, definition.id(),
                            "reward type=" + VillagerActionRegistry.canonicalTypeId(action) + " result=" + result.status()),
                    () -> VillagerQuestSavedData.get(context.level()).changed());
            if (!outcome.success()) return outcome;
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

}
