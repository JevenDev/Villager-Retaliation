package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialoguePlaceholders;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class VillagerActionExecutor {
    private VillagerActionExecutor() {
    }

    public static VillagerActionResult execute(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        if (context == null || action == null || action.kind() == VillagerActionDefinition.Kind.NONE) {
            return VillagerActionResult.EMPTY;
        }
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        if (inheritedReplacements != null) {
            replacements.putAll(inheritedReplacements);
        }

        return switch (action.kind()) {
            case NOTIFICATION -> executeNotification(context, action, replacements);
            case TRACKER -> VillagerActionResult.tracker(action.flashTracker());
            case FORCED_DIALOGUE -> executeForcedDialogue(context, action, replacements);
            case QUEST -> executeQuestAction(context, action, replacements);
            case EXPERIENCE -> awardExperience(context, action.amount()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case REPUTATION -> changeReputation(context, action.amount()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case GOSSIP -> spreadGossip(context, action.amount()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case MEMORY -> rememberMemory(context, action.memoryTag()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case LOOT -> giveLoot(context, action.lootTable()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case NONE -> VillagerActionResult.EMPTY;
        };
    }

    public static boolean awardExperience(DialogueContext context, int amount) {
        if (context == null || amount <= 0) {
            return false;
        }
        context.player().giveExperiencePoints(amount);
        return true;
    }

    public static boolean changeReputation(DialogueContext context, int amount) {
        if (context == null || amount == 0) {
            return false;
        }
        VillagerReputationManager.addDialogueReputation(context.level(), context.villager(), context.player(), amount);
        return true;
    }

    public static boolean spreadGossip(DialogueContext context, int amount) {
        if (context == null || amount == 0) {
            return false;
        }
        VillagerGossipHooks.spreadReputation(context.level(), context.villager(), context.player().getUUID(), amount);
        return true;
    }

    public static boolean rememberMemory(DialogueContext context, ResourceLocation memoryTag) {
        if (context == null || memoryTag == null) {
            return false;
        }
        VillageEventMemory.remember(
                context.level(),
                memoryTag,
                context.villager().blockPosition(),
                context.villager(),
                context.player());
        return true;
    }

    public static boolean giveLoot(DialogueContext context, ResourceLocation lootTableId) {
        if (context == null || lootTableId == null) {
            return false;
        }
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        LootTable table = context.level().getServer().reloadableRegistries().getLootTable(lootTableKey);
        if (table == LootTable.EMPTY) {
            return false;
        }

        boolean gaveAny = false;
        LootParams params = new LootParams.Builder(context.level())
                .withLuck(context.player().getLuck())
                .create(LootContextParamSets.EMPTY);
        for (ItemStack stack : table.getRandomItems(params, context.random())) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack reward = stack.copy();
            ItemStack noticeStack = reward.copy();
            if (!context.player().addItem(reward) && !reward.isEmpty()) {
                context.player().drop(reward, false);
            }
            VillagerInteractionService.sendReceivedItemNotice(context.player(), context.villager(), noticeStack);
            gaveAny = true;
        }
        return gaveAny;
    }

    private static VillagerActionResult executeNotification(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> replacements) {
        String trigger = action.notificationTrigger().isBlank() ? "quest.trigger" : action.notificationTrigger();
        String fallback = action.text().isBlank() ? "Quest updated: {quest}" : action.text();
        VillagerNotifications.sendHud(
                context.player(),
                context.level(),
                context.villager(),
                trigger,
                replacements,
                VillagerDialogueResources.resolveTemplate(fallback, replacements),
                VillagerReputationNoticeKind.QUEST
        );
        return VillagerActionResult.success();
    }

    private static VillagerActionResult executeForcedDialogue(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> replacements) {
        boolean ran = ForcedDialogueService.tryTriggerQuestDialogue(
                context.level(),
                context.villager(),
                context.player(),
                action.forcedDialogue(),
                replacements);
        return ran ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
    }

    private static VillagerActionResult executeQuestAction(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return VillagerQuestService.performAction(context, action.questId(), action.questAction())
                .map(outcome -> {
                    Map<String, String> replacements = new LinkedHashMap<>(inheritedReplacements);
                    replacements.putAll(outcome.replacements());
                    List<String> overrideLines = action.linesForStatus(outcome.status());
                    String text = overrideLines.isEmpty()
                            ? outcome.text()
                            : VillagerDialogueResources.resolveTemplate(
                                    overrideLines.get(context.random().nextInt(overrideLines.size())),
                                    replacements);
                    return new VillagerActionResult(
                            true,
                            outcome.lineId(),
                            text,
                            replacements,
                            false);
                })
                .orElse(VillagerActionResult.EMPTY);
    }
}
