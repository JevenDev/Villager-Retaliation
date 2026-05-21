package com.jvn.villagerretaliation.util;

import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

public final class VillagerLocale {
    public static final String DEFAULT_LOCALE = "en_us";

    private VillagerLocale() {
    }

    public static String locale(ServerPlayer player) {
        if (player == null) {
            return DEFAULT_LOCALE;
        }
        String language = player.clientInformation().language();
        if (language == null || language.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return normalize(language);
    }

    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
