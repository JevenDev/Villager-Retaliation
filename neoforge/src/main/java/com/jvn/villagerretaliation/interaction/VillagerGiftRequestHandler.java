package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.VillagerTakenItemTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

public final class VillagerGiftRequestHandler {
    private VillagerGiftRequestHandler() {
    }

    public static void handle(ServerPlayer player, int entityId, int inventorySlot) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_GIFTS.get()) {
            VillagerInteractionService.sendNotice(player, entityId, "interaction.gift_unavailable");
            return;
        }
        InteractionTargetContext target = InteractionRequestValidator
                .requireGiftConversation(player, entityId)
                .orElse(null);
        if (target == null) {
            return;
        }

        Villager villager = target.villager();
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, player)) {
            InteractionRequestValidator.endConversationWithRefusal(target, "interaction.keep_distance");
            return;
        }
        if (inventorySlot < 0 || inventorySlot >= 36) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.gift_invalid");
            return;
        }

        ItemStack selectedStack = player.getInventory().getItem(inventorySlot);
        if (selectedStack.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.gift_empty_slot");
            return;
        }
        if (!VillagerInventoryAccess.canAddItems(villager, List.of(selectedStack.copy()))) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.gift_inventory_full");
            return;
        }

        ServerLevel level = target.level();
        Optional<VillagerTakenItemTracker.TakenItemOwner> takenItemOwner =
                VillagerTakenItemTracker.owner(selectedStack);
        ItemStack giftedStack = player.getInventory().removeItem(inventorySlot, selectedStack.getCount());
        VillagerTakenItemTracker.clear(giftedStack);
        player.getInventory().setChanged();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        VillagerGiftPreferences.GiftPreference giftPreference = VillagerGiftPreferences.evaluate(level, villager, giftedStack);
        int reputationValue = adjustedGiftReputation(level, villager, giftPreference);
        VillagerGiftKnowledgeService.rememberGiftResult(level, player, profession, giftedStack, giftPreference);
        Boolean giftAdviceLikedResult = giftAdviceLikedResult(giftPreference.reaction());
        if (giftAdviceLikedResult != null) {
            VillagerInteractionTracker.markGiftAdviceResult(
                    level,
                    villager,
                    player,
                    itemId(giftedStack),
                    itemName(giftedStack),
                    VillagerGiftKnowledgeService.professionKey(profession),
                    VillagerInteractionTextUtil.professionName(profession, "villager").toLowerCase(java.util.Locale.ROOT),
                    VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                    giftAdviceLikedResult
            );
        }
        VillagerReputationManager.addGiftReputation(level, villager, player, reputationValue);
        VillagerGiftKeepsakes.storeGift(level, villager, player, giftedStack, giftPreference);
        rememberGearGift(level, villager, player, giftedStack);
        VillageEventMemory.rememberGift(
                level,
                villager.blockPosition(),
                villager,
                player,
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                itemName(giftedStack),
                giftPreference.reaction(),
                reputationValue
        );
        VillagerQuestService.onGiftGiven(level, player, villager, giftedStack, giftPreference.reaction(), reputationValue);
        VillagerMoodService.recordGift(level, villager, player, giftPreference.reaction(), reputationValue);
        reduceDialogueAnnoyanceFromGift(level, villager, player, reputationValue);
        sendGiftNotice(player, villager, giftedStack, reputationValue);
        VillagerInteractionService.focusVillagerOnPlayer(villager, player);
        VillagerInteractionService.playGiftFeedback(level, villager, reputationValue);
        VillagerAmbientIndicatorService.onGiftReceived(villager, reputationValue);

        DialogueContext giftContext = VillagerInteractionService.createDialogueContext(level, player, villager);
        String responseText = giftResponseText(giftContext, giftPreference, giftedStack, takenItemOwner, villager);
        VillagerInteractionService.sendDialogueReputation(player, villager, level);
        VillagerInteractionService.sendPersonalVillagerChat(player, villager, responseText);
    }

    private static void reduceDialogueAnnoyanceFromGift(ServerLevel level, Villager villager, ServerPlayer player, int reputationValue) {
        int divisor = VillagerRetaliationConfig.GIFT_ANNOYANCE_REDUCTION_DIVISOR.get();
        if (reputationValue <= 0 || divisor <= 0) {
            return;
        }
        int reduction = Math.max(1, reputationValue / divisor);
        VillagerInteractionTracker.reduceRepeatedDialogueUseCounts(level, villager, player, reduction);
    }

    private static String giftResponseKey(VillagerGiftPreferences.GiftPreference giftPreference) {
        String scope = giftPreference.professionSpecific() ? "profession" : "global";
        String reaction = giftPreference.reaction().name().toLowerCase(java.util.Locale.ROOT);
        return "gift_response." + scope + "." + reaction;
    }

    private static String giftResponseText(
            DialogueContext context,
            VillagerGiftPreferences.GiftPreference giftPreference,
            ItemStack giftedStack,
            Optional<VillagerTakenItemTracker.TakenItemOwner> takenItemOwner,
            Villager villager) {
        Map<String, String> replacements = new java.util.HashMap<>(Map.of(
                "gift_item", giftedStack.getHoverName().getString(),
                "item", itemName(giftedStack),
                "gift_item_id", itemId(giftedStack),
                "item_id", itemId(giftedStack)
        ));
        if (takenItemOwner.isPresent()) {
            VillagerTakenItemTracker.TakenItemOwner owner = takenItemOwner.get();
            replacements.put("owner_villager", owner.villagerName());
            String ownershipKey = owner.villagerId().equals(villager.getUUID())
                    ? "gift_response.owned.self"
                    : "gift_response.owned.other";
            String ownershipResponse = VillagerDialogueResources.message(context, ownershipKey, replacements).orElse("");
            if (!ownershipResponse.isBlank()) {
                return ownershipResponse;
            }
        }
        String responseKey = giftPreference.responseKey();
        if (responseKey != null && !responseKey.isBlank()) {
            String customResponse = VillagerDialogueResources.message(context, responseKey, replacements).orElse("");
            if (!customResponse.isBlank()) {
                return customResponse;
            }
        }
        return VillagerDialogueResources.message(context, giftResponseKey(giftPreference), replacements).orElse("");
    }

    private static Boolean giftAdviceLikedResult(VillagerGiftPreferences.GiftReaction reaction) {
        return switch (reaction) {
            case LOVED, LIKED -> true;
            case DISLIKED, HATED -> false;
            case NEUTRAL -> null;
        };
    }

    private static int adjustedGiftReputation(
            ServerLevel level,
            Villager villager,
            VillagerGiftPreferences.GiftPreference giftPreference) {
        int reputationValue = giftPreference.reputationValue();
        if (!VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS)) {
            return reputationValue;
        }
        if (reputationValue > 0) {
            int kindnessBonus = VillagerSocialAttributeBehavior.positiveBonus(
                    level,
                    villager,
                    VillagerSocialAttribute.KINDNESS,
                    giftPreference.reaction() == VillagerGiftPreferences.GiftReaction.LOVED ? 3 : 2,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS
            );
            int charmBonus = VillagerSocialAttributeBehavior.positiveBonus(
                    level,
                    villager,
                    VillagerSocialAttribute.CHARM,
                    1,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS
            );
            return reputationValue + kindnessBonus + charmBonus;
        }
        if (reputationValue < 0) {
            int maxSoftening = giftPreference.reaction() == VillagerGiftPreferences.GiftReaction.HATED ? 1 : 2;
            int kindnessSoftening = VillagerSocialAttributeBehavior.positiveBonus(
                    level,
                    villager,
                    VillagerSocialAttribute.KINDNESS,
                    maxSoftening,
                    VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS
            );
            return Math.min(-1, reputationValue + kindnessSoftening);
        }
        return reputationValue;
    }

    private static void rememberGearGift(ServerLevel level, Villager villager, ServerPlayer player, ItemStack giftedStack) {
        String gearKind = gearKind(giftedStack);
        if (!gearKind.isBlank()) {
            VillagerInteractionTracker.rememberGearReport(level, villager, player, gearKind);
        }
    }

    private static String gearKind(ItemStack stack) {
        if (VillagerRetaliationVillagerWeapons.isUsableWeapon(stack)) {
            return "weapon";
        }
        Equipable equipable = Equipable.get(stack);
        return equipable == null ? "" : "armor";
    }

    private static void sendGiftNotice(ServerPlayer player, Villager villager, ItemStack giftedStack, int reputationValue) {
        VillagerReputationNoticeKind kind = reputationValue < 0
                ? VillagerReputationNoticeKind.GIFT_DISLIKED
                : reputationValue > 0 ? VillagerReputationNoticeKind.GIFT_LIKED : VillagerReputationNoticeKind.GIFT_NEUTRAL;
        String reaction = reputationValue < 0 ? "Disliked gift" : reputationValue > 0 ? "Liked gift" : "Accepted gift";
        String trigger = reputationValue < 0 ? "gift.disliked" : reputationValue > 0 ? "gift.liked" : "gift.neutral";
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                trigger,
                VillagerNotifications.replacements("item", itemName(giftedStack), "villager", displayName(villager)),
                reaction + ": " + itemName(giftedStack),
                kind
        );
    }

    private static String itemName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        return stack.getCount() > 1 ? stack.getCount() + "x " + name : name;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String displayName(Villager villager) {
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
    }
}
