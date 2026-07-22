package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.network.OpenVillagerDuelPayload;
import com.jvn.villagerretaliation.network.VillagerDuelRequestPayload;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DuelRequestHandler {
    private DuelRequestHandler() {}

    public static void handle(ServerPlayer player, VillagerDuelRequestPayload request) {
        Entity entity = player.serverLevel().getEntity(request.entityId());
        if (!(entity instanceof Villager villager) || !VillagerConversationService.validate(player, villager)) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.duel.unavailable.invalid"));
            return;
        }
        if (request.action() == VillagerDuelRequestPayload.Action.OPEN) {
            sendStatus(player, villager);
            return;
        }
        DuelService.StartResult result = DuelService.start(player, villager, request.loadout(), request.stake());
        if (!result.started()) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.duel.unavailable." + result.reason().name().toLowerCase()));
            sendStatus(player, villager);
        }
    }

    private static void sendStatus(ServerPlayer player, Villager villager) {
        DuelAvailability status = DuelService.availability(player.serverLevel(), player, villager);
        String currency = VillagerCurrencyResources.text(player.server).pluralName();
        PacketDistributor.sendToPlayer(player, new OpenVillagerDuelPayload(
                villager.getId(), VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                status.available(), status.reason(), status.villagerWins(), status.villagerLosses(),
                status.consecutiveLosses(), status.cooldownTicksRemaining(), status.playerCurrency(),
                status.villagerCurrency(), currency));
    }
}
