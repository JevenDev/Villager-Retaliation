package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record VillageAllegianceView(
        String homeVillage,
        String currentVillage,
        HomeStatus homeStatus,
        boolean inVillage,
        boolean atHome) {
    private static final int TEXT_LIMIT = 256;
    public static final VillageAllegianceView EMPTY = new VillageAllegianceView(
            "Unknown", "Outside a tracked village", HomeStatus.UNKNOWN, false, false);

    public static void encode(RegistryFriendlyByteBuf buffer, VillageAllegianceView view) {
        VillageAllegianceView safe = view == null ? EMPTY : view;
        buffer.writeUtf(safe.homeVillage(), TEXT_LIMIT);
        buffer.writeUtf(safe.currentVillage(), TEXT_LIMIT);
        buffer.writeEnum(safe.homeStatus());
        buffer.writeBoolean(safe.inVillage());
        buffer.writeBoolean(safe.atHome());
    }

    public static VillageAllegianceView decode(RegistryFriendlyByteBuf buffer) {
        return new VillageAllegianceView(
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readEnum(HomeStatus.class),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    public enum HomeStatus {
        KNOWN,
        WANDERER,
        UNKNOWN
    }
}
