package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class VillagerReputationNetworking {
    private VillagerReputationNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(VillagerReputationSyncPayload.TYPE, VillagerReputationSyncPayload.STREAM_CODEC, VillagerReputationNetworking::handleClientSync);
    }

    public static void sendReputation(ServerPlayer player, Villager villager, int reputation) {
        PacketDistributor.sendToPlayer(player, new VillagerReputationSyncPayload(
                villager.getId(),
                villager.getUUID(),
                reputation,
                VillagerReputationLevel.fromReputation(reputation)
        ));
    }

    private static void handleClientSync(VillagerReputationSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            context.enqueueWork(() -> com.jvn.villagerretaliation.client.reputation.VillagerReputationClientCache.accept(payload));
        }
    }
}
