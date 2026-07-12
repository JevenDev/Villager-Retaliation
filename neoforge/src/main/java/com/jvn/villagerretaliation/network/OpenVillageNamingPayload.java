package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenVillageNamingPayload(
        BlockPos bellPosition,
        VillageAllegianceId villageId,
        String currentName,
        boolean canRename,
        int trustedResidents,
        int requiredResidents) implements CustomPacketPayload {
    private static final int NAME_LIMIT = 32;
    public static final Type<OpenVillageNamingPayload> TYPE = VillagerPayloads.type("open_village_naming");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillageNamingPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillageNamingPayload::encode, OpenVillageNamingPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillageNamingPayload payload) {
        buffer.writeBlockPos(payload.bellPosition());
        buffer.writeUUID(payload.villageId().value());
        buffer.writeUtf(payload.currentName(), NAME_LIMIT);
        buffer.writeBoolean(payload.canRename());
        buffer.writeVarInt(payload.trustedResidents());
        buffer.writeVarInt(payload.requiredResidents());
    }

    private static OpenVillageNamingPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillageNamingPayload(
                buffer.readBlockPos(),
                new VillageAllegianceId(buffer.readUUID()),
                buffer.readUtf(NAME_LIMIT),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
