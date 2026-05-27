package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InteractionRequestValidator {
    private InteractionRequestValidator() {
    }

    public static Optional<InteractionTargetContext> requireDialogueConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.unavailable",
                "interaction.conversation_ended",
                true
        );
    }

    public static Optional<InteractionTargetContext> requireTradeConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.trade_unavailable",
                "interaction.conversation_ended",
                false
        );
    }

    public static Optional<InteractionTargetContext> requireInventoryConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.inventory_unavailable",
                "interaction.conversation_ended",
                false
        );
    }

    public static Optional<InteractionTargetContext> requireGiftConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.gift_unavailable",
                "interaction.conversation_ended",
                false
        );
    }

    public static Optional<InteractionTargetContext> requireRecruitConversation(ServerPlayer player, int entityId) {
        return requireActiveConversation(
                player,
                entityId,
                "interaction.recruit_unavailable",
                "interaction.conversation_ended",
                false
        );
    }

    public static void endConversationWithRefusal(
            InteractionTargetContext target,
            String refusalNoticeKey) {
        VillagerConversationService.endForPlayer(target.player(), true);
        VillagerAmbientIndicatorService.onTradeRefused(target.villager());
        VillagerInteractionService.sendVillagerNotice(target.player(), target.villager(), refusalNoticeKey);
    }

    private static Optional<InteractionTargetContext> requireActiveConversation(
            ServerPlayer player,
            int entityId,
            String unavailableNoticeKey,
            String conversationEndedNoticeKey,
            boolean sendConversationEndedPayload) {
        Optional<InteractionTargetContext> target = requireVillagerTarget(player, entityId, unavailableNoticeKey);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        InteractionTargetContext context = target.get();
        if (VillagerConversationService.validate(player, context.villager())) {
            return Optional.of(context);
        }
        if (sendConversationEndedPayload) {
            PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(entityId, ""));
        }
        VillagerInteractionService.sendVillagerNotice(player, context.villager(), conversationEndedNoticeKey);
        return Optional.empty();
    }

    private static Optional<InteractionTargetContext> requireVillagerTarget(
            ServerPlayer player,
            int entityId,
            String unavailableNoticeKey) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager)
                || !VillagerInteractionService.canUseInteractionSystem(player, villager)) {
            VillagerInteractionService.sendNotice(player, entityId, unavailableNoticeKey);
            return Optional.empty();
        }
        return Optional.of(new InteractionTargetContext(player.serverLevel(), player, villager));
    }
}
