package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InteractionRequestValidator {
    private InteractionRequestValidator() {
    }

    public static Optional<InteractionTargetContext> requireDialogueConversation(ServerPlayer player, int entityId) {
        return requireDialogueConversation(player, entityId, null);
    }

    public static Optional<InteractionTargetContext> requireDialogueConversation(ServerPlayer player, int entityId,
            java.util.UUID greetingRequestId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.unavailable",
                "interaction.conversation_ended",
                true,
                true,
                greetingRequestId
        );
    }

    public static Optional<InteractionTargetContext> requireTradeConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.trade_unavailable",
                "interaction.conversation_ended",
                false,
                false
        );
    }

    public static Optional<InteractionTargetContext> requireRepairConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(player, entityId, "interaction.trade_unavailable",
                "interaction.conversation_ended", false, false);
    }

    public static Optional<InteractionTargetContext> requireRoutineChatConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.unavailable",
                "interaction.conversation_ended",
                false,
                false
        );
    }

    public static Optional<InteractionTargetContext> requireInventoryConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.inventory_unavailable",
                "interaction.conversation_ended",
                false,
                false
        );
    }

    public static Optional<InteractionTargetContext> requireGiftConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.gift_unavailable",
                "interaction.conversation_ended",
                false,
                false
        );
    }

    public static Optional<InteractionTargetContext> requireRecruitConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.recruit_unavailable",
                "interaction.conversation_ended",
                false,
                false
        );
    }

    public static Optional<InteractionTargetContext> requireStudyConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.study.unavailable",
                "interaction.conversation_ended",
                false,
                false
        );
    }

    public static void endConversationWithRefusal(
            InteractionTargetContext target,
            String refusalNoticeKey) {
        endConversationWithRefusal(target, refusalNoticeKey, null);
    }

    public static void endConversationWithRefusal(InteractionTargetContext target, String refusalNoticeKey,
            java.util.UUID greetingRequestId) {
        if (greetingRequestId != null) VillagerInteractionService.sendVillagerNotice(
                target.player(), target.villager(), refusalNoticeKey, greetingRequestId);
        VillagerConversationService.endForPlayer(target.player(), true);
        VillagerAmbientIndicatorService.onTradeRefused(target.villager());
        if (greetingRequestId == null) VillagerInteractionService.sendVillagerNotice(target.player(), target.villager(), refusalNoticeKey);
    }

    private static Optional<InteractionTargetContext> requireActiveConversation(
            ServerPlayer player,
            int entityId,
            String unavailableNoticeKey,
            String conversationEndedNoticeKey,
            boolean sendConversationEndedPayload,
            boolean allowForcedSessionTarget) {
        return requireActiveConversation(player, entityId, unavailableNoticeKey, conversationEndedNoticeKey,
                sendConversationEndedPayload, allowForcedSessionTarget, null);
    }

    private static Optional<InteractionTargetContext> requireActiveConversation(ServerPlayer player, int entityId,
            String unavailableNoticeKey, String conversationEndedNoticeKey, boolean sendConversationEndedPayload,
            boolean allowForcedSessionTarget, java.util.UUID greetingRequestId) {
        Optional<InteractionTargetContext> target = requireVillagerTarget(
                player,
                entityId,
                unavailableNoticeKey,
                allowForcedSessionTarget,
                greetingRequestId
        );
        if (target.isEmpty()) {
            return Optional.empty();
        }
        InteractionTargetContext context = target.get();
        if (VillagerConversationService.validate(player, context.villager())) {
            return Optional.of(context);
        }
        if (greetingRequestId != null) VillagerInteractionService.sendVillagerNotice(
                player, context.villager(), conversationEndedNoticeKey, greetingRequestId);
        if (sendConversationEndedPayload) {
            PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(entityId, ""));
        }
        if (greetingRequestId == null) VillagerInteractionService.sendVillagerNotice(player, context.villager(), conversationEndedNoticeKey);
        return Optional.empty();
    }

    private static Optional<InteractionTargetContext> requireVillagerTarget(
            ServerPlayer player,
            int entityId,
            String unavailableNoticeKey,
            boolean allowForcedSessionTarget) {
        return requireVillagerTarget(player, entityId, unavailableNoticeKey, allowForcedSessionTarget, null);
    }

    private static Optional<InteractionTargetContext> requireVillagerTarget(ServerPlayer player, int entityId,
            String unavailableNoticeKey, boolean allowForcedSessionTarget, java.util.UUID greetingRequestId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            VillagerInteractionService.sendNotice(player, entityId, unavailableNoticeKey, greetingRequestId);
            return Optional.empty();
        }
        if (VillagerBehaviorSuppressionPolicy.suppresses(
                villager, VillagerBehaviorSuppressionPolicy.Behavior.INTERACTION_MENUS)) {
            if (greetingRequestId != null) VillagerInteractionService.sendVillagerNotice(
                    player, villager, "interaction.incapacitated", greetingRequestId);
            VillagerConversationService.endForPlayer(player, true);
            if (greetingRequestId == null) VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.incapacitated");
            return Optional.empty();
        }
        boolean canUseNormalTarget = VillagerInteractionService.canUseInteractionSystem(player, villager);
        boolean forcedSessionTarget = allowForcedSessionTarget
                && VillagerConversationService.isForced(player, villager)
                && ForcedDialogueService.hasSession(player, villager);
        if (!canUseNormalTarget && !forcedSessionTarget) {
            VillagerInteractionService.sendNotice(player, entityId, unavailableNoticeKey, greetingRequestId);
            return Optional.empty();
        }
        return Optional.of(new InteractionTargetContext(player.serverLevel(), player, villager));
    }
}
