package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FilterPolicyChangePayload(int fieldId, int value) implements CustomPacketPayload {
    public static final Type<FilterPolicyChangePayload> TYPE =
            VillagerPayloads.type("filter_policy_change");
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterPolicyChangePayload> STREAM_CODEC =
            VillagerPayloads.codec(FilterPolicyChangePayload::encode, FilterPolicyChangePayload::decode);

    public FilterPolicyChangePayload(VillagerFilterPolicy.PolicyField field, int value) {
        this(field == null ? -1 : field.networkId(), value);
    }

    public VillagerFilterPolicy.PolicyField requestedField() {
        return VillagerFilterPolicy.PolicyField.fromNetworkId(this.fieldId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, FilterPolicyChangePayload payload) {
        buffer.writeByte(payload.fieldId());
        buffer.writeInt(payload.value());
    }

    private static FilterPolicyChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new FilterPolicyChangePayload(buffer.readByte(), buffer.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
