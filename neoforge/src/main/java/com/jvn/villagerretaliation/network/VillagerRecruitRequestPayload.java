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
        HIRE_FIVE_DAYS,
        HIRE_SEVEN_DAYS,
        HIRE_FIFTEEN_DAYS,
        HIRE_THIRTY_DAYS,
        EXTEND_ONE_DAY,
        EXTEND_THREE_DAYS,
        EXTEND_FIVE_DAYS,
        EXTEND_SEVEN_DAYS,
        EXTEND_FIFTEEN_DAYS,
        EXTEND_THIRTY_DAYS,
        VIEW_CONTRACT,
        OPEN_JOB_INVENTORY,
        SHOW_STORAGE,
        DEPOSIT_EARNINGS,
        REMOVE_STORAGE,
        SHOW_PAYMENT_STORAGE,
        REMOVE_PAYMENT_STORAGE,
        TOGGLE_AUTO_PAYMENT,
        PROMPT_END_HIRE_CONFIRMATION,
        DECLINE_END_HIRE_CONFIRMATION,
        END_HIRE,
        VIEW_ROLE,
        SET_ROLE_COMBAT,
        SET_ROLE_MINING,
        SET_ROLE_LOGGING,
        SET_ROLE_FARMING,
        SET_ROLE_FISHING,
        SET_ROLE_BREWING,
        SET_ROLE_NAVIGATION,
        SET_ROLE_ANIMAL_HANDLING,
        SET_ROLE_NITWIT,
        VIEW_WORK_STATUS,
        TOGGLE_WORK_ENABLED,
        TOGGLE_USE_ASSIGNED_SUPPLIES,
        TOGGLE_AUTO_DEPOSIT_OUTPUTS,
        CONFIGURE_COMBAT,
        CONFIGURE_MINING,
        CONFIGURE_LOGGING,
        CONFIGURE_FARMING,
        CONFIGURE_FISHING,
        CONFIGURE_BREWING,
        CONFIGURE_NAVIGATION,
        CONFIGURE_ANIMAL_HANDLING,
        CONFIGURE_NITWIT,
        STOP_BREWING,
        FOLLOW,
        STAY_HERE,
        STOP_FOLLOWING
    }
}
