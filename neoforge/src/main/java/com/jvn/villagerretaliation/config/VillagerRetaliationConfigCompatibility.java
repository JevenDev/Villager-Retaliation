package com.jvn.villagerretaliation.config;

import com.mojang.logging.LogUtils;
import io.wispforest.owo.config.ConfigWrapper;
import io.wispforest.owo.config.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.slf4j.Logger;

final class VillagerRetaliationConfigCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EXPERIMENTAL_TRADE_MIGRATION_PROPERTY =
            "experimentalTradeFeaturesMigrationVersion";

    private VillagerRetaliationConfigCompatibility() {
    }

    static boolean shouldMigrateCraftsmanSkillGrowth(Path configPath) {
        if (!Files.exists(configPath)) {
            return true;
        }
        try {
            return !configTextHasProperty(Files.readString(configPath), "craftsman");
        } catch (IOException exception) {
            LOGGER.warn("Failed to inspect existing config for the Craftsman skill-growth setting", exception);
            return false;
        }
    }

    static boolean shouldDisableLegacyExperimentalTradeFeatures(Path configPath) {
        if (!Files.exists(configPath)) {
            Path configDirectory = configPath.getParent();
            return configDirectory != null
                    && (Files.exists(configDirectory.resolve("villagerretaliation-common.toml"))
                    || Files.exists(configDirectory.resolve("villagerretaliation-client.toml")));
        }
        try {
            return configTextRequiresExperimentalTradeMigration(Files.readString(configPath));
        } catch (IOException exception) {
            LOGGER.warn("Failed to inspect existing config for the experimental trade-feature migration", exception);
            return false;
        }
    }

    static boolean configTextRequiresExperimentalTradeMigration(String configText) {
        return !configTextHasProperty(configText, EXPERIMENTAL_TRADE_MIGRATION_PROPERTY);
    }

    static boolean configTextHasProperty(String configText, String propertyName) {
        if (configText == null || propertyName == null || propertyName.isBlank()) {
            return false;
        }
        return Pattern.compile(
                        "(?m)^\\s*\"?" + Pattern.quote(propertyName) + "\"?\\s*:")
                .matcher(configText)
                .find();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean inheritLegacyCraftsmanSkillGrowth(ConfigWrapper<?> config) {
        Option logging = config.optionForKey(new Option.Key("balance.hiredWorkSkillGrowth.logging"));
        Option craftsman = config.optionForKey(new Option.Key("balance.hiredWorkSkillGrowth.craftsman"));
        if (logging == null || craftsman == null) {
            return false;
        }
        try {
            craftsman.set(logging.value());
            LOGGER.info("Initialized Craftsman skill growth from the existing Logging value for compatibility");
            return true;
        } catch (Exception exception) {
            LOGGER.warn("Failed to initialize the Craftsman skill-growth setting from Logging", exception);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean disableLegacyExperimentalTradeFeatures(ConfigWrapper<?> config) {
        Option enabled = config.optionForKey(new Option.Key("trade.enableSkillTradeOverhaul"));
        Option migrationVersion = config.optionForKey(
                new Option.Key("trade." + EXPERIMENTAL_TRADE_MIGRATION_PROPERTY));
        if (enabled == null || migrationVersion == null) {
            return false;
        }
        try {
            enabled.set(false);
            migrationVersion.set(1);
            LOGGER.info(
                    "Disabled experimental skill trades, trade cycling, and trade requests for the one-time config migration");
            return true;
        } catch (Exception exception) {
            LOGGER.warn("Failed to disable legacy experimental trade features", exception);
            return false;
        }
    }
}
