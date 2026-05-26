package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerProfileSyncPayload(
        int entityId,
        UUID villagerId,
        String professionKey,
        int generatedVersion,
        int knowledge,
        int guts,
        int proficiency,
        int kindness,
        int charm) implements CustomPacketPayload {
    public static final Type<VillagerProfileSyncPayload> TYPE = VillagerPayloads.type("villager_profile_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerProfileSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerProfileSyncPayload::encode, VillagerProfileSyncPayload::decode);

    public VillagerSocialAttributes attributes() {
        return new VillagerSocialAttributes(this.knowledge, this.guts, this.proficiency, this.kindness, this.charm);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerProfileSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeUtf(payload.professionKey(), 128);
        buffer.writeVarInt(payload.generatedVersion());
        buffer.writeVarInt(payload.knowledge());
        buffer.writeVarInt(payload.guts());
        buffer.writeVarInt(payload.proficiency());
        buffer.writeVarInt(payload.kindness());
        buffer.writeVarInt(payload.charm());
    }

    private static VillagerProfileSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerProfileSyncPayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
