package com.jvn.villagerretaliation.quest.provider;

import java.util.Locale;
import java.util.Optional;

public enum QuestProviderDeathProtection {
    NONE("none"),
    WHILE_ACTIVE("while_active"),
    AFTER_START("after_start");

    private final String serializedName;

    QuestProviderDeathProtection(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public static Optional<QuestProviderDeathProtection> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(NONE);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (QuestProviderDeathProtection policy : values()) {
            if (policy.serializedName.equals(normalized)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }
}
