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
}
