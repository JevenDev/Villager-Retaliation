package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.DialogueReputationEffect;
import com.jvn.villagerretaliation.dialogue.DialogueReputationService;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerInteractionService {
    private VillagerInteractionService() {
    }

    public static boolean shouldHandleInteraction(Villager villager, ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get()
                && !player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG)
                && canUseInteractionSystem(player, villager);
    }

    public static InteractionResult handleAdultVillagerRightClick(Villager villager, ServerPlayer player) {
        if (player.isShiftKeyDown() && VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.get()) {
            return openTrading(player, villager, true);
        }

        if (!VillagerConversationService.start(player, villager)) {
            sendNotice(player, villager.getId(), "That villager is busy right now.");
            return InteractionResult.FAIL;
        }
        openInteractionScreen(player, villager);
        focusVillagerOnPlayer(villager, player);
        return InteractionResult.SUCCESS;
    }

    public static void openInteractionScreen(ServerPlayer player, Villager villager) {
        PacketDistributor.sendToPlayer(player, new OpenVillagerInteractionPayload(
                villager.getId(),
                villager.getDisplayName().getString(),
                professionName(villager.getVillagerData().getProfession())
        ));
    }

    public static void handleDialogueRequest(ServerPlayer player, int entityId, DialogueRequestType requestType) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "That villager is no longer available.");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendNotice(player, entityId, "The conversation has ended.");
            return;
        }
        focusVillagerOnPlayer(villager, player);

        ServerLevel level = player.serverLevel();
        VillagerInteractionTracker.InteractionState interactionState = VillagerInteractionTracker.getState(level, villager, player);
        int reputation = VillagerReputationManager.getReputation(level, villager, player.getUUID());
        VillagerReputationLevel reputationLevel = VillagerReputationLevel.fromReputation(reputation);
        DialogueContext context = new DialogueContext(
                level,
                player,
                villager,
                villager.getVillagerData().getProfession(),
                reputation,
                reputationLevel,
                interactionState.firstConversation(),
                VillageEventMemory.recentNear(level, villager.blockPosition()),
                villager.getRandom()
        );
        VillagerDialogueService.DialogueResult result = VillagerDialogueService.select(
                context,
                requestType,
                interactionState.recentDialogueIds()
        );
        DialogueReputationEffect reputationEffect = DialogueReputationService.apply(context, requestType, interactionState);
        String responseText = reputationEffect.responseOverride() == null ? result.text() : reputationEffect.responseOverride();
        VillagerInteractionTracker.rememberDialogue(level, villager, player, requestType, result.lineId());
        PacketDistributor.sendToPlayer(player, new VillagerDialogueResponsePayload(villager.getId(), requestType, responseText));
    }

    public static void handleTradeRequest(ServerPlayer player, int entityId) {
        Villager villager = resolveVillager(player, entityId);
        if (villager == null) {
            sendNotice(player, entityId, "That villager cannot trade right now.");
            return;
        }
        if (!VillagerConversationService.validate(player, villager)) {
            sendNotice(player, entityId, "The conversation has ended.");
            return;
        }
        focusVillagerOnPlayer(villager, player);
        VillagerConversationService.endForPlayer(player, true);
        openTrading(player, villager, true);
    }

    public static void handleConversationEndRequest(ServerPlayer player, int entityId) {
        VillagerConversationService.endForPlayer(player, false);
    }

    public static InteractionResult openTrading(ServerPlayer player, Villager villager, boolean sendFailureMessage) {
        if (!canUseInteractionSystem(player, villager)) {
            if (sendFailureMessage) {
                sendNotice(player, villager.getId(), "That villager cannot trade right now.");
            }
            return InteractionResult.FAIL;
        }
        if (VillagerRetaliationHandler.blockTradingIfHostile(villager, player)) {
            if (villager.level() instanceof ServerLevel level
                    && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                    <= VillagerReputationLevel.HOSTILE.trustRank()) {
                VillagerReputationAdvancements.onTradeRefusedDueToLowReputation(player);
            }
            if (sendFailureMessage) {
                sendNotice(player, villager.getId(), "This villager refuses to trade with you.");
            }
            return InteractionResult.FAIL;
        }
        if (villager.getOffers().isEmpty()) {
            villager.setUnhappyCounter(40);
            if (sendFailureMessage) {
                sendNotice(player, villager.getId(), "This villager has no trades available.");
            }
            return InteractionResult.CONSUME;
        }

        return villager.mobInteract(player, InteractionHand.MAIN_HAND);
    }

    private static Villager resolveVillager(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !canUseInteractionSystem(player, villager)) {
            return null;
        }
        return villager;
    }

    public static boolean canUseInteractionSystem(ServerPlayer player, Villager villager) {
        double maxDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return villager.isAlive()
                && !villager.isBaby()
                && !villager.isSleeping()
                && !villager.isTrading()
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(villager) <= maxDistance * maxDistance;
    }

    private static void sendNotice(ServerPlayer player, int entityId, String text) {
        PacketDistributor.sendToPlayer(player, new VillagerInteractionNoticePayload(entityId, text));
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static String professionName(VillagerProfession profession) {
        String rawName = profession.name();
        if (rawName == null || rawName.isBlank() || "none".equals(rawName)) {
            return "Unemployed";
        }
        StringBuilder builder = new StringBuilder(rawName.length());
        boolean capitalizeNext = true;
        for (char character : rawName.replace('_', ' ').toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                builder.append(character);
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static void focusVillagerOnPlayer(Villager villager, ServerPlayer player) {
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }
}
