package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerRecruitRequestPayload(
        int entityId, Action action, HiredVillagerRole selectedRole, long expectedRevision) implements CustomPacketPayload {
    public static final Type<VillagerRecruitRequestPayload> TYPE = VillagerPayloads.type("villager_recruit_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerRecruitRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerRecruitRequestPayload::encode, VillagerRecruitRequestPayload::decode);

    public VillagerRecruitRequestPayload(int entityId, Action action) {
        this(entityId, action, null, -1L);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerRecruitRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
        buffer.writeBoolean(payload.selectedRole() != null);
        if (payload.selectedRole() != null) {
            buffer.writeEnum(payload.selectedRole());
        }
        buffer.writeVarLong(payload.expectedRevision());
    }

    private static VillagerRecruitRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        Action action = buffer.readEnum(Action.class);
        HiredVillagerRole selectedRole = buffer.readBoolean() ? buffer.readEnum(HiredVillagerRole.class) : null;
        long expectedRevision = buffer.readVarLong();
        return new VillagerRecruitRequestPayload(entityId, action, selectedRole, expectedRevision);
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
        EXTEND_MAX_DAYS,
        VIEW_CONTRACT,
        OPEN_JOB_INVENTORY,
        SHOW_STORAGE,
        DEPOSIT_EARNINGS,
        REMOVE_STORAGE,
        SHOW_PAYMENT_STORAGE,
        REMOVE_PAYMENT_STORAGE,
        TOGGLE_AUTO_PAYMENT,
        START_MOUNT_ASSIGNMENT,
        UNASSIGN_MOUNT,
        TOGGLE_MOUNTED_TRAVEL,
        PROMPT_END_HIRE_CONFIRMATION,
        DECLINE_END_HIRE_CONFIRMATION,
        END_HIRE,
        VIEW_ROLE,
        SET_ROLE_COMBAT,
        SET_ROLE_HUNTING,
        SET_ROLE_MINING,
        SET_ROLE_LOGGING,
        SET_ROLE_FARMING,
        SET_ROLE_FISHING,
        SET_ROLE_BREWING,
        SET_ROLE_CRAFTSMAN,
        SET_ROLE_BUILDER,
        SET_ROLE_ANIMAL_HANDLING,
        SET_ROLE_NITWIT,
        VIEW_WORK_STATUS,
        TOGGLE_WORK_ENABLED,
        TOGGLE_USE_ASSIGNED_SUPPLIES,
        TOGGLE_AUTO_DEPOSIT_OUTPUTS,
        CONFIGURE_COMBAT,
        CONFIGURE_HUNTING,
        CONFIGURE_MINING,
        TOGGLE_HORIZONTAL_MINING_FLOOR_PATCHING,
        CONFIGURE_LOGGING,
        CONFIGURE_FARMING,
        CONFIGURE_FISHING,
        CONFIGURE_BREWING,
        CONFIGURE_BUILDER,
        CONFIGURE_ANIMAL_HANDLING,
        CONFIGURE_NITWIT,
        CYCLE_CRAFTSMAN_MODE,
        STOP_BREWING,
        STOP_BUILDER_BUILD,
        FOLLOW,
        STAY_HERE,
        STOP_FOLLOWING,
        STOP_STAYING_HERE,
        SET_ROLE_COOK,
        SET_ROLE_SMELTER,
        SET_ROLE_COURIER,
        PROMPT_PARTY_RECRUIT_CONFIRMATION,
        DECLINE_PARTY_RECRUIT_CONFIRMATION,
        PARTY_RECRUIT,
        PROMPT_PARTY_DISMISS_CONFIRMATION,
        DECLINE_PARTY_DISMISS_CONFIRMATION,
        PARTY_DISMISS,
        CYCLE_PARTY_COMBAT_MODE,
        CYCLE_PARTY_ATTACK_MODE,
        CYCLE_PARTY_DROP_COLLECTION,
        UNEQUIP_PARTY_WEAPONS
    }
}
