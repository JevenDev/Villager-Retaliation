package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.party.PartyAttackMode;
import com.jvn.villagerretaliation.party.PartyCombatMode;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PartyActionRequestPayload(
        Action action,
        UUID targetId,
        UUID invitationId,
        boolean enabled,
        PartyAttackMode attackMode,
        PartyCombatMode combatMode) implements CustomPacketPayload {
    public static final Type<PartyActionRequestPayload> TYPE = VillagerPayloads.type("party_action_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(PartyActionRequestPayload::encode, PartyActionRequestPayload::decode);

    public PartyActionRequestPayload(Action action) {
        this(action, null, null, false, null, null);
    }

    public PartyActionRequestPayload(Action action, UUID targetId, UUID invitationId) {
        this(action, targetId, invitationId, false, null, null);
    }

    public PartyActionRequestPayload(Action action, UUID targetId, UUID invitationId, boolean enabled) {
        this(action, targetId, invitationId, enabled, null, null);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyActionRequestPayload payload) {
        buffer.writeEnum(payload.action());
        buffer.writeBoolean(payload.targetId() != null);
        if (payload.targetId() != null) {
            buffer.writeUUID(payload.targetId());
        }
        buffer.writeBoolean(payload.invitationId() != null);
        if (payload.invitationId() != null) {
            buffer.writeUUID(payload.invitationId());
        }
        buffer.writeBoolean(payload.enabled());
        buffer.writeBoolean(payload.attackMode() != null);
        if (payload.attackMode() != null) {
            buffer.writeEnum(payload.attackMode());
        }
        buffer.writeBoolean(payload.combatMode() != null);
        if (payload.combatMode() != null) {
            buffer.writeEnum(payload.combatMode());
        }
    }

    private static PartyActionRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID targetId = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID invitationId = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean enabled = buffer.readBoolean();
        PartyAttackMode attackMode = buffer.readBoolean() ? buffer.readEnum(PartyAttackMode.class) : null;
        PartyCombatMode combatMode = buffer.readBoolean() ? buffer.readEnum(PartyCombatMode.class) : null;
        return new PartyActionRequestPayload(action, targetId, invitationId, enabled, attackMode, combatMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SEND_INVITATION,
        ACCEPT_INVITATION,
        DECLINE_INVITATION,
        LEAVE_PARTY,
        REMOVE_PLAYER,
        DISBAND_PARTY,
        SET_COMBAT_MODE,
        SET_ATTACK_MODE,
        SET_SHARED_VILLAGER_INVENTORIES,
        SET_ADMIN_PRIVILEGES,
        SET_QUICK_COMMANDS_ENABLED
    }
}
