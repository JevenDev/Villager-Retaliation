package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageRenameRequestPayload(
        BlockPos bellPosition,
        VillageAllegianceId villageId,
        String name) implements CustomPacketPayload {
    private static final int NAME_LIMIT = 64;
    public static final Type<VillageRenameRequestPayload> TYPE = VillagerPayloads.type("village_rename_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageRenameRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillageRenameRequestPayload::encode, VillageRenameRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillageRenameRequestPayload payload) {
        buffer.writeBlockPos(payload.bellPosition());
        buffer.writeUUID(payload.villageId().value());
        buffer.writeUtf(payload.name(), NAME_LIMIT);
    }

    private static VillageRenameRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillageRenameRequestPayload(
                buffer.readBlockPos(),
                new VillageAllegianceId(buffer.readUUID()),
                buffer.readUtf(NAME_LIMIT));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
