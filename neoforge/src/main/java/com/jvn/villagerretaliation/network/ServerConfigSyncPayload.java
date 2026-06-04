package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerConfigSyncPayload(boolean showVillagerNameTags) implements CustomPacketPayload {
    public static final Type<ServerConfigSyncPayload> TYPE = VillagerPayloads.type("server_config_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ServerConfigSyncPayload::encode, ServerConfigSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ServerConfigSyncPayload payload) {
        buffer.writeBoolean(payload.showVillagerNameTags());
    }

    private static ServerConfigSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ServerConfigSyncPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
