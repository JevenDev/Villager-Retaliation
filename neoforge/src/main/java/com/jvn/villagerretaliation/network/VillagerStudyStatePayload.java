package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.study.VillagerStudyState;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerStudyStatePayload(
        int entityId,
        UUID villagerId,
        boolean featureEnabled,
        String skillId,
        int activeTicks,
        int durationTicks,
        boolean paused,
        long cooldownRemainingTicks
) implements CustomPacketPayload {
    private static final int MAX_SKILL_ID_LENGTH = 64;
    public static final Type<VillagerStudyStatePayload> TYPE = VillagerPayloads.type("villager_study_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerStudyStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerStudyStatePayload::encode, VillagerStudyStatePayload::decode);

    public static VillagerStudyStatePayload create(
            int entityId,
            UUID villagerId,
            boolean featureEnabled,
            VillagerStudyState state,
            long gameTime
    ) {
        VillagerStudyState safeState = state == null ? VillagerStudyState.NONE : state;
        return new VillagerStudyStatePayload(
                entityId,
                villagerId,
                featureEnabled,
                safeState.skill() == null ? "" : safeState.skill().serializedName(),
                safeState.activeTicks(),
                com.jvn.villagerretaliation.study.VillagerStudyService.configuredDurationTicks(),
                safeState.paused(),
                safeState.cooldownRemaining(gameTime));
    }

    public boolean studying() {
        return !this.skillId.isBlank();
    }

    public boolean active() {
        return studying() && !this.paused;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerStudyStatePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeBoolean(payload.featureEnabled());
        buffer.writeUtf(payload.skillId(), MAX_SKILL_ID_LENGTH);
        buffer.writeVarInt(Math.max(0, payload.activeTicks()));
        buffer.writeVarInt(Math.max(1, payload.durationTicks()));
        buffer.writeBoolean(payload.paused());
        buffer.writeVarLong(Math.max(0L, payload.cooldownRemainingTicks()));
    }

    private static VillagerStudyStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerStudyStatePayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_SKILL_ID_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
