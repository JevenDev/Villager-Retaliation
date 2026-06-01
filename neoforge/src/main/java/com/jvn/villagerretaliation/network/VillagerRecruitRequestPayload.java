package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerRecruitRequestPayload(int entityId, Action action) implements CustomPacketPayload {
    public static final Type<VillagerRecruitRequestPayload> TYPE = VillagerPayloads.type("villager_recruit_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerRecruitRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerRecruitRequestPayload::encode, VillagerRecruitRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerRecruitRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
    }

    private static VillagerRecruitRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerRecruitRequestPayload(buffer.readVarInt(), buffer.readEnum(Action.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        HIRE_ONE_DAY,
        HIRE_THREE_DAYS,
        HIRE_SEVEN_DAYS,
        EXTEND_ONE_DAY,
        EXTEND_THREE_DAYS,
        EXTEND_FIVE_DAYS,
        EXTEND_SEVEN_DAYS,
        EXTEND_FIFTEEN_DAYS,
        EXTEND_THIRTY_DAYS,
        VIEW_CONTRACT,
        OPEN_JOB_INVENTORY,
        SHOW_STORAGE,
        REMOVE_STORAGE,
        END_HIRE,
        SET_ROLE_COMBAT,
        SET_ROLE_MINING,
        SET_ROLE_LOGGING,
        SET_ROLE_FARMING,
        SET_ROLE_BREWING,
        SET_ROLE_NAVIGATION,
        SET_ROLE_ANIMAL_HANDLING,
        FOLLOW
    }
}
