package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.toucanlib.neoforge.network.ToucanNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class VillagerReputationNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private VillagerReputationNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        ToucanNetwork network = ToucanNetwork.create(VillagerRetaliation.MOD_ID, PROTOCOL_VERSION, event);
        network.safePlayToClientThreaded(
                VillagerReputationSyncPayload.TYPE,
                VillagerReputationSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.reputation.VillagerReputationClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                FearedVillagerPulsePayload.TYPE,
                FearedVillagerPulsePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache",
                "accept"
        );
    }

    public static void sendReputation(ServerPlayer player, AbstractVillager villager, int reputation) {
        PacketDistributor.sendToPlayer(player, new VillagerReputationSyncPayload(
                villager.getId(),
                villager.getUUID(),
                reputation,
                VillagerReputationLevel.fromReputation(reputation)
        ));
    }

    public static void sendFearedPulse(AbstractVillager villager, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(villager, new FearedVillagerPulsePayload(villager.getId(), ticks));
    }
}
