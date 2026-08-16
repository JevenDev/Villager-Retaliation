package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Selects one server-validated attribute from the open attribute-filter reference item. */
public record AttributeFilterSelectPayload(
        VillagerAttributeFilterData.AttributeType attributeType,
        String value,
        boolean inverted) implements CustomPacketPayload {
    public static final Type<AttributeFilterSelectPayload> TYPE =
            VillagerPayloads.type("attribute_filter_select");
    public static final StreamCodec<RegistryFriendlyByteBuf, AttributeFilterSelectPayload> STREAM_CODEC =
            VillagerPayloads.codec(AttributeFilterSelectPayload::encode, AttributeFilterSelectPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, AttributeFilterSelectPayload payload) {
        buffer.writeUtf(payload.attributeType().id(), 64);
        buffer.writeUtf(payload.value(), 512);
        buffer.writeBoolean(payload.inverted());
    }

    private static AttributeFilterSelectPayload decode(RegistryFriendlyByteBuf buffer) {
        VillagerAttributeFilterData.AttributeType type =
                VillagerAttributeFilterData.AttributeType.byId(buffer.readUtf(64));
        String value = buffer.readUtf(512);
        boolean inverted = buffer.readBoolean();
        return new AttributeFilterSelectPayload(type, value, inverted);
    }

    public VillagerAttributeFilterData.Attribute attribute() {
        return attributeType == null ? null : new VillagerAttributeFilterData.Attribute(attributeType, value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
