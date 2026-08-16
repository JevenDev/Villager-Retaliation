package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

final class VillagerPayloads {
    private VillagerPayloads() {
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(VillagerRetaliation.id(path));
    }

    static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> codec(
            BiConsumer<RegistryFriendlyByteBuf, T> encoder,
            Function<RegistryFriendlyByteBuf, T> decoder) {
        return StreamCodec.of(encoder::accept, decoder::apply);
    }

    static int readCollectionSize(RegistryFriendlyByteBuf buffer, int maxSize, String fieldName) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maxSize) {
            throw new IllegalArgumentException(fieldName + " size " + size + " is outside 0.." + maxSize);
        }
        return size;
    }
}
