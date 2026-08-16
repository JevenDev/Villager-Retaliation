package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WeaponAimingDialogueDelayPreferencePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<WeaponAimingDialogueDelayPreferencePayload> TYPE =
            VillagerPayloads.type("weapon_aiming_dialogue_delay_preference");
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponAimingDialogueDelayPreferencePayload> STREAM_CODEC =
            VillagerPayloads.codec(
                    WeaponAimingDialogueDelayPreferencePayload::encode,
                    WeaponAimingDialogueDelayPreferencePayload::decode);

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            WeaponAimingDialogueDelayPreferencePayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static WeaponAimingDialogueDelayPreferencePayload decode(RegistryFriendlyByteBuf buffer) {
        return new WeaponAimingDialogueDelayPreferencePayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
