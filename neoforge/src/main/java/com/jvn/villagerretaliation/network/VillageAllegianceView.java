package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record VillageAllegianceView(
        String homeVillage,
        String currentVillage,
        String location,
        String lifecycle,
        String assignmentSource,
        String loyalty,
        boolean canReassign) {
    private static final int TEXT_LIMIT = 256;
    public static final VillageAllegianceView EMPTY = new VillageAllegianceView(
            "Unknown", "Outside a tracked village", "Unknown", "Unknown", "Unknown", "Unknown", false);

    public static void encode(RegistryFriendlyByteBuf buffer, VillageAllegianceView view) {
        VillageAllegianceView safe = view == null ? EMPTY : view;
        buffer.writeUtf(safe.homeVillage(), TEXT_LIMIT);
        buffer.writeUtf(safe.currentVillage(), TEXT_LIMIT);
        buffer.writeUtf(safe.location(), TEXT_LIMIT);
        buffer.writeUtf(safe.lifecycle(), TEXT_LIMIT);
        buffer.writeUtf(safe.assignmentSource(), TEXT_LIMIT);
        buffer.writeUtf(safe.loyalty(), TEXT_LIMIT);
        buffer.writeBoolean(safe.canReassign());
    }

    public static VillageAllegianceView decode(RegistryFriendlyByteBuf buffer) {
        return new VillageAllegianceView(
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readBoolean());
    }
}
