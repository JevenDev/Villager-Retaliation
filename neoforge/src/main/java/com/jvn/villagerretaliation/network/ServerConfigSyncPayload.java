package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.config.VillagerStatDisplayMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerConfigSyncPayload(
        boolean showVillagerNameTags,
        boolean showQuestIndicators,
        boolean villagerGiftsEnabled,
        boolean skillTradeFeaturesEnabled,
        VillagerStatDisplayMode villagerStatDisplayMode) implements CustomPacketPayload {
    public static final Type<ServerConfigSyncPayload> TYPE = VillagerPayloads.type("server_config_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ServerConfigSyncPayload::encode, ServerConfigSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ServerConfigSyncPayload payload) {
        buffer.writeBoolean(payload.showVillagerNameTags());
        buffer.writeBoolean(payload.showQuestIndicators());
        buffer.writeBoolean(payload.villagerGiftsEnabled());
        buffer.writeBoolean(payload.skillTradeFeaturesEnabled());
        buffer.writeEnum(payload.villagerStatDisplayMode());
    }

    private static ServerConfigSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ServerConfigSyncPayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readEnum(VillagerStatDisplayMode.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
