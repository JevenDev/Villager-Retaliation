package com.jvn.villagerretaliation.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public record SettlementBuildingPreviewRequestPayload(UUID town, int requestId, ResourceLocation structure,
                                                      BlockPos anchor, Rotation rotation, UUID builder) implements CustomPacketPayload {
    public static final Type<SettlementBuildingPreviewRequestPayload> TYPE = VillagerPayloads.type("settlement_building_preview_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBuildingPreviewRequestPayload> STREAM_CODEC = VillagerPayloads.codec(
            (buffer, payload) -> {
                buffer.writeUUID(payload.town());
                buffer.writeVarInt(payload.requestId());
                buffer.writeResourceLocation(payload.structure());
                buffer.writeBlockPos(payload.anchor());
                buffer.writeEnum(payload.rotation());
                buffer.writeBoolean(payload.builder() != null);
                if (payload.builder() != null) buffer.writeUUID(payload.builder());
            }, buffer -> new SettlementBuildingPreviewRequestPayload(buffer.readUUID(), buffer.readVarInt(), buffer.readResourceLocation(),
                    buffer.readBlockPos(), buffer.readEnum(Rotation.class), buffer.readBoolean() ? buffer.readUUID() : null));

    public SettlementBuildingPreviewRequestPayload {
        Objects.requireNonNull(town);
        Objects.requireNonNull(structure);
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(rotation);
        if (requestId < 0) throw new IllegalArgumentException("Invalid building preview request");
        anchor = anchor.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
