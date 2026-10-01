package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record SettlementPolicyOrderLink(UUID order, UUID policy, String name, UUID binding,
                                        SettlementSupplyPolicy.State state, SettlementSupplyPolicy.BindingState execution,
                                        String blocker) {
    public SettlementPolicyOrderLink {
        Objects.requireNonNull(order);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(binding);
        Objects.requireNonNull(state);
        Objects.requireNonNull(execution);
        if (name == null || name.isBlank() || name.length() > 64 || blocker == null || blocker.length() > 128)
            throw new IllegalArgumentException("Invalid managed order link");
    }

    static List<SettlementPolicyOrderLink> validate(ProductionTarget target, List<SettlementPolicyOrderLink> links) {
        var copy = List.copyOf(links);
        if (copy.size() > 32 || target.town() == null && !copy.isEmpty()
                || copy.stream().map(SettlementPolicyOrderLink::order).distinct().count() != copy.size())
            throw new IllegalArgumentException("Invalid managed order links");
        return copy;
    }

    static void write(RegistryFriendlyByteBuf buffer, List<SettlementPolicyOrderLink> links) {
        buffer.writeVarInt(links.size());
        for (var link : links) {
            buffer.writeUUID(link.order());
            buffer.writeUUID(link.policy());
            buffer.writeUtf(link.name(), 64);
            buffer.writeUUID(link.binding());
            buffer.writeEnum(link.state());
            buffer.writeEnum(link.execution());
            buffer.writeUtf(link.blocker(), 128);
        }
    }

    static List<SettlementPolicyOrderLink> read(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, 32, "Managed order links");
        var links = new ArrayList<SettlementPolicyOrderLink>();
        for (int index = 0; index < size; index++) links.add(new SettlementPolicyOrderLink(
                buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64), buffer.readUUID(),
                buffer.readEnum(SettlementSupplyPolicy.State.class), buffer.readEnum(SettlementSupplyPolicy.BindingState.class),
                buffer.readUtf(128)));
        return List.copyOf(links);
    }
}
