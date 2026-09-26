package com.jvn.villagerretaliation.network;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageRenameResultPayload(UUID requestId, Result result, String name,
                                         int requiredResidents) implements CustomPacketPayload {
    public static final Type<VillageRenameResultPayload> TYPE = VillagerPayloads.type("village_rename_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageRenameResultPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.requestId());
                buffer.writeEnum(payload.result());
                buffer.writeUtf(payload.name(), 32);
                buffer.writeVarInt(payload.requiredResidents());
            }, buffer -> new VillageRenameResultPayload(buffer.readUUID(), buffer.readEnum(Result.class),
                    buffer.readUtf(32), buffer.readVarInt()));

    public Component message() {
        if (this.result == Result.SUCCESS) {
            return Component.translatable("villagerretaliation.village_naming.renamed", this.name);
        }
        return Component.translatable("villagerretaliation.village_naming.error."
                + this.result.name().toLowerCase(Locale.ROOT), this.requiredResidents);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Result {
        SUCCESS,
        TOO_FAR,
        BANNER_REQUIRED,
        VILLAGE_CHANGED,
        INSUFFICIENT_STANDING,
        INVALID_NAME,
        NAME_IN_USE,
        TRY_AGAIN
    }
}
