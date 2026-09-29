package com.jvn.villagerretaliation.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record ProductionTarget(UUID town, UUID subject) {
    public ProductionTarget {
        Objects.requireNonNull(subject);
    }

    void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.town != null);
        if (this.town != null) buffer.writeUUID(this.town);
        buffer.writeUUID(this.subject);
    }

    static ProductionTarget read(RegistryFriendlyByteBuf buffer) {
        return new ProductionTarget(buffer.readBoolean() ? buffer.readUUID() : null, buffer.readUUID());
    }
}
