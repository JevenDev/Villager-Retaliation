package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record VillageAllegianceView(
        String homeVillage,
        String currentVillage,
        HomeStatus homeStatus,
        boolean inVillage,
        boolean atHome,
        String prompt,
        String askHomeLabel,
        String askCurrentVillageLabel,
        String reassignLabel,
        String homeAnswer,
        String currentVillageAnswer) {
    private static final int TEXT_LIMIT = 256;
    private static final int DIALOGUE_TEXT_LIMIT = 512;
    public static final VillageAllegianceView EMPTY = new VillageAllegianceView(
            "Unknown",
            "Outside a tracked village",
            HomeStatus.UNKNOWN,
            false,
            false,
            "",
            "",
            "",
            "",
            "",
            "");

    public static void encode(RegistryFriendlyByteBuf buffer, VillageAllegianceView view) {
        VillageAllegianceView safe = view == null ? EMPTY : view;
        buffer.writeUtf(safe.homeVillage(), TEXT_LIMIT);
        buffer.writeUtf(safe.currentVillage(), TEXT_LIMIT);
        buffer.writeEnum(safe.homeStatus());
        buffer.writeBoolean(safe.inVillage());
        buffer.writeBoolean(safe.atHome());
        buffer.writeUtf(safe.prompt(), DIALOGUE_TEXT_LIMIT);
        buffer.writeUtf(safe.askHomeLabel(), DIALOGUE_TEXT_LIMIT);
        buffer.writeUtf(safe.askCurrentVillageLabel(), DIALOGUE_TEXT_LIMIT);
        buffer.writeUtf(safe.reassignLabel(), DIALOGUE_TEXT_LIMIT);
        buffer.writeUtf(safe.homeAnswer(), DIALOGUE_TEXT_LIMIT);
        buffer.writeUtf(safe.currentVillageAnswer(), DIALOGUE_TEXT_LIMIT);
    }

    public static VillageAllegianceView decode(RegistryFriendlyByteBuf buffer) {
        return new VillageAllegianceView(
                buffer.readUtf(TEXT_LIMIT),
                buffer.readUtf(TEXT_LIMIT),
                buffer.readEnum(HomeStatus.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(DIALOGUE_TEXT_LIMIT),
                buffer.readUtf(DIALOGUE_TEXT_LIMIT),
                buffer.readUtf(DIALOGUE_TEXT_LIMIT),
                buffer.readUtf(DIALOGUE_TEXT_LIMIT),
                buffer.readUtf(DIALOGUE_TEXT_LIMIT),
                buffer.readUtf(DIALOGUE_TEXT_LIMIT));
    }

    public enum HomeStatus {
        KNOWN,
        WANDERER,
        UNKNOWN
    }
}