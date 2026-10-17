package com.jvn.villagerretaliation.exploration;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server sends only a world identity and, optionally, the village the player is standing in. */
public record MapObservationPayload(UUID world, ResourceLocation dimension, BlockPos position, String village, String name) implements CustomPacketPayload {
    public static final Type<MapObservationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("villagerretaliation", "map_observation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapObservationPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.world); b.writeResourceLocation(p.dimension); b.writeBlockPos(p.position); b.writeUtf(p.village, 128); b.writeUtf(p.name, 128);
    }, b -> new MapObservationPayload(b.readUUID(), b.readResourceLocation(), b.readBlockPos(), b.readUtf(128), b.readUtf(128)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    @EventBusSubscriber(modid = "villagerretaliation", bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() ->
                    com.jvn.villagerretaliation.client.exploration.ExploredMapClient.accept(payload)));
        }
    }
}
