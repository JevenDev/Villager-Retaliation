package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class VillagerItemFilterInteractionHandler {
    static final String ALLOWLIST_OPTION_ID = "item_filter_use_allowlist";
    static final String DENYLIST_OPTION_ID = "item_filter_use_denylist";
    static final String NEVERMIND_OPTION_ID = "item_filter_nevermind";

    private VillagerItemFilterInteractionHandler() {
    }

    static InteractionResult open(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !VillagerRetaliationItems.isItemFilter(player.getMainHandItem())) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.item_filter.adult_hired_only");
            return InteractionResult.FAIL;
        }
        if (!HiredVillagerContractService.isHired(level, villager)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.item_filter.not_hired");
            return InteractionResult.FAIL;
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.item_filter.requires_hirer");
            return InteractionResult.FAIL;
        }
        if (!VillagerConversationService.startForced(player, villager)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.busy");
            return InteractionResult.FAIL;
        }
        closeActiveContainer(player);
        VillagerInteractionScreenOpener.openForced(player, villager, options(), true);
        VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.item_filter.prompt");
        VillagerInteractionService.focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    static boolean handleDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        if (!handlesOption(optionId)) {
            return false;
        }
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager)
                || !VillagerConversationService.isForced(player, villager)
                || !VillagerInteractionService.canUseForcedInteractionSystem(player, villager)
                || !VillagerConversationService.validate(player, villager)
                || villager.isBaby()
                || !HiredVillagerContractService.isHired(player.serverLevel(), villager)
                || !HiredVillagerContractService.isHiredBy(player.serverLevel(), villager, player)) {
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        if (NEVERMIND_OPTION_ID.equals(optionId)) {
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        ItemStack heldFilter = player.getMainHandItem();
        if (!VillagerRetaliationItems.isItemFilter(heldFilter)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.item_filter.missing");
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }

        VillagerItemFilterData.Mode selectedMode = DENYLIST_OPTION_ID.equals(optionId)
                ? VillagerItemFilterData.Mode.DENYLIST
                : VillagerItemFilterData.Mode.ALLOWLIST;
        VillagerItemFilterService.AssignmentResult assignment =
                VillagerItemFilterService.assignHeldFilter(player, villager, selectedMode);
        if (!assignment.assigned()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.item_filter.missing");
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        String noticeKey;
        if (assignment.droppedOldFilter()) {
            noticeKey = "interaction.item_filter.replaced_dropped";
        } else if (assignment.replaced()) {
            noticeKey = "interaction.item_filter.replaced";
        } else if (selectedMode == VillagerItemFilterData.Mode.DENYLIST) {
            noticeKey = "interaction.item_filter.assigned_denylist";
        } else {
            noticeKey = "interaction.item_filter.assigned_allowlist";
        }
        VillagerInteractionService.sendVillagerNotice(player, villager, noticeKey);
        VillagerConversationService.endForPlayer(player, true);
        return true;
    }

    static boolean handlesOption(String optionId) {
        return ALLOWLIST_OPTION_ID.equals(optionId)
                || DENYLIST_OPTION_ID.equals(optionId)
                || NEVERMIND_OPTION_ID.equals(optionId);
    }

    static List<DialogueOptionDefinition> options() {
        return List.of(
                DialogueOptionDefinition.simple(
                        ALLOWLIST_OPTION_ID,
                        Component.translatable("villagerretaliation.gui.item_filter.assign.allowlist").getString(),
                        DialogueRequestType.QUESTION,
                        0),
                DialogueOptionDefinition.simple(
                        DENYLIST_OPTION_ID,
                        Component.translatable("villagerretaliation.gui.item_filter.assign.denylist").getString(),
                        DialogueRequestType.QUESTION,
                        1),
                DialogueOptionDefinition.simple(
                        NEVERMIND_OPTION_ID,
                        Component.translatable("villagerretaliation.gui.item_filter.assign.nevermind").getString(),
                        DialogueRequestType.QUESTION,
                        2));
    }

    private static void closeActiveContainer(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }
}
