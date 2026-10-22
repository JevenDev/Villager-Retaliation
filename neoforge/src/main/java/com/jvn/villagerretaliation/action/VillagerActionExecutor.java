package com.jvn.villagerretaliation.action;

import com.mojang.logging.LogUtils;
import com.jvn.villagerretaliation.combat.VillagerWeaponDrawService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.scene.SceneLaunchService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class VillagerActionExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private VillagerActionExecutor() {
    }

    public static VillagerActionResult execute(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return VillagerActionRegistry.execute(context, action, inheritedReplacements).legacyResult();
    }

    static VillagerActionResult executeDirect(
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
            case KNOWLEDGE -> com.jvn.villagerretaliation.dialogue.Knowledge.execute(context, action).legacyResult();
            case TUTORIAL -> com.jvn.villagerretaliation.quest.tutorial.TutorialService.execute(context, action).legacyResult();
            case COMPANION_UNLOCK, COMPANION_BOND -> com.jvn.villagerretaliation.party.CompanionRelationshipService.execute(context, action).legacyResult();
            case COMPANION_SERVICE -> com.jvn.villagerretaliation.party.CompanionServiceOffers.execute(context, action).legacyResult();
            case FOLLOWER -> com.jvn.villagerretaliation.quest.follower.QuestFollowerService.execute(context, action).legacyResult();
            case NOTIFICATION -> executeNotification(context, action, replacements);
            case TRACKER -> VillagerActionResult.tracker(action.flashTracker());
            case FORCED_DIALOGUE -> executeForcedDialogue(context, action, replacements).legacyResult();
            case QUEST -> executeQuestAction(context, action, replacements);
            case CAMPAIGN_PAYMENT -> com.jvn.villagerretaliation.campaign.CampaignPaymentService.execute(context, action).legacyResult();
            case CAMPAIGN_REWARD -> com.jvn.villagerretaliation.campaign.CampaignRewardService.execute(context, action).legacyResult();
            case CAMPAIGN_STANDING -> com.jvn.villagerretaliation.campaign.CampaignStandingService.execute(context, action).legacyResult();
            case CAMPAIGN_FACT -> com.jvn.villagerretaliation.campaign.CampaignFactService.execute(context, action).legacyResult();
            case CAMPAIGN_OUTCOME -> com.jvn.villagerretaliation.campaign.CampaignOutcomeService.execute(context, action).legacyResult();
            case QUEST_TRANSITION -> VillagerQuestService.applyCompiledTransition(context, action.questTransition(), replacements);
            case EXPERIENCE -> awardExperience(context, action.amount()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case REPUTATION -> changeReputation(context, action.amount()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case GOSSIP -> spreadGossip(context, action.amount()) ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case PROFILE_ATTRIBUTE -> changeProfileAttribute(context, action.factKey(), action.amount())
                    ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case MEMORY -> rememberMemory(context, action.memoryTag(), action.memoryScope())
                    ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case LOOT -> grantLoot(context, action.lootTable()).resolved() ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case SET_TAG -> executeFact(context, action, replacements);
            case CLEAR_TAG -> executeFact(context, action, replacements);
            case SET_VARIABLE -> executeFact(context, action, replacements);
            case COUNTER -> executeFact(context, action, replacements);
            case DRAW_WEAPON -> VillagerWeaponDrawService.draw(context.villager(), action.amount())
                    ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case START_SCENE -> SceneLaunchService.launch(context, action).accepted()
                    ? VillagerActionResult.success() : VillagerActionResult.EMPTY;
            case NONE -> VillagerActionResult.EMPTY;
        };
    }

    public static boolean awardExperience(DialogueContext context, int amount) {
        return NarrativeActions.awardExperience(context, amount);
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

    public static boolean changeProfileAttribute(DialogueContext context, String attributeName, int amount) {
        if (context == null || amount == 0) {
            return false;
        }
        VillagerSocialAttribute attribute = VillagerSocialAttribute.bySerializedName(attributeName);
        if (attribute == null) {
            return false;
        }
        return VillagerProfileManager.adjustAttribute(
                context.level(), context.villager(), attribute, amount);
    }

    public static boolean rememberMemory(DialogueContext context, ResourceLocation memoryTag) {
        return rememberMemory(context, memoryTag, VillageEventMemory.MemoryScope.BOTH);
    }

    public static boolean rememberMemory(
            DialogueContext context,
            ResourceLocation memoryTag,
            VillageEventMemory.MemoryScope memoryScope) {
        if (context == null || memoryTag == null) {
            return false;
        }
        return VillageEventMemory.remember(
                context.level(),
                memoryTag,
                context.villager().blockPosition(),
                context.villager(),
                context.player(),
                memoryScope).changed();
    }

    public static boolean giveLoot(DialogueContext context, ResourceLocation lootTableId) {
        return grantLoot(context, lootTableId).gaveAny();
    }

    private static LootGrant grantLoot(DialogueContext context, ResourceLocation lootTableId) {
        var grant = NarrativeActions.grantLoot(context, lootTableId, stack -> {
            if (context.villager() != null) VillagerInteractionService.sendReceivedItemNotice(context.player(), context.villager(), stack);
        });
        if (!grant.resolved()) LOGGER.warn("{}", grant.diagnostic());
        return new LootGrant(grant.resolved(), grant.gaveAny());
    }

    private record LootGrant(boolean resolved, boolean gaveAny) {
    }

    private static VillagerActionResult executeNotification(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> replacements) {
        String trigger = action.notificationTrigger().isBlank() ? "quest.trigger" : action.notificationTrigger();
        Map<String, String> notificationReplacements = new LinkedHashMap<>(replacements);
        if (action.questId() != null) {
            String questTitle = VillagerQuestResources.quest(context.level().getServer(), action.questId())
                    .map(definition -> definition.title())
                    .orElse(action.questId().toString());
            notificationReplacements.putIfAbsent("quest", questTitle);
            notificationReplacements.putIfAbsent("quest_id", action.questId().toString());
        }
        String fallback = action.text().isBlank() ? "Quest updated: {quest}" : action.text();
        if (context.villager() == null) {
            context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal(VillagerDialogueResources.resolveTemplate(fallback, notificationReplacements)));
            return VillagerActionResult.success();
        }
        VillagerNotifications.sendHud(
                context.player(),
                context.level(),
                context.villager(),
                trigger,
                notificationReplacements,
                VillagerDialogueResources.resolveTemplate(fallback, notificationReplacements),
                VillagerReputationNoticeKind.QUEST
        );
        return VillagerActionResult.success();
    }

    static ActionResult executeForcedDialogue(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        if (inheritedReplacements != null) replacements.putAll(inheritedReplacements);
        return ForcedDialogueService.triggerQuestDialogueResult(
                context.level(), context.villager(), context.player(), action.forcedDialogue(), replacements);
    }

    private static VillagerActionResult executeQuestAction(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return VillagerQuestService.performAction(context, action.questId(), action.questAction())
                .map(outcome -> {
                    Map<String, String> replacements = new LinkedHashMap<>(inheritedReplacements);
                    replacements.putAll(outcome.replacements());
                    replacements.put("quest_action_status", outcome.status());
                    List<String> overrideLines = action.linesForStatus(outcome.status());
                    List<String> overrideKeys = action.lineKeysForStatus(outcome.status());
                    String fallback = overrideLines.isEmpty()
                            ? outcome.text()
                            : VillagerDialogueResources.resolveTemplate(
                                    overrideLines.get(context.random().nextInt(overrideLines.size())),
                                    replacements);
                    String text = overrideKeys.isEmpty()
                            ? fallback
                            : VillagerDialogueResources.message(
                                            context,
                                            overrideKeys.get(context.random().nextInt(overrideKeys.size())),
                                            replacements)
                                    .orElse(fallback);
                    return new VillagerActionResult(
                            true,
                            outcome.lineId(),
                            text,
                            replacements,
                            false);
                })
                .orElse(VillagerActionResult.EMPTY);
    }

    private static VillagerActionResult executeFact(DialogueContext context, VillagerActionDefinition action,
            Map<String, String> replacements) {
        return FactActions.execute(context, action, replacements, VillagerQuestFacts.get(context.level()),
                (runtime, quest, stage, source) -> VillagerQuestService.syncQuestStage(context, quest, stage, source));
    }

    static VillagerActionResult factResult(
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements,
            String scopeKey,
            String value,
            int counterValue) {
        return FactActions.result(action, inheritedReplacements, scopeKey, value, counterValue);
    }
}
