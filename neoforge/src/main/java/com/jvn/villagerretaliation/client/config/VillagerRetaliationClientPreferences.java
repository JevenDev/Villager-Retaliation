package com.jvn.villagerretaliation.client.config;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class VillagerRetaliationClientPreferences {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SHOW_VILLAGER_NAME_TAGS = "showVillagerNameTags";
    private static final String CONFIRM_QUEST_ABANDONMENT = "confirmQuestAbandonment";
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("villagerretaliation-client.properties");

    private static boolean loaded;
    private static boolean showVillagerNameTags = true;
    private static boolean confirmQuestAbandonment = true;

    private VillagerRetaliationClientPreferences() {
    }

    public static boolean showVillagerNameTags() {
        ensureLoaded();
        return showVillagerNameTags;
    }

    public static boolean toggleShowVillagerNameTags() {
        ensureLoaded();
        showVillagerNameTags = !showVillagerNameTags;
        save();
        return showVillagerNameTags;
    }

    public static boolean confirmQuestAbandonment() {
        ensureLoaded();
        return confirmQuestAbandonment;
    }

    public static void confirmQuestAbandonment(boolean value) {
        ensureLoaded();
        if (confirmQuestAbandonment != value) {
            confirmQuestAbandonment = value;
            save();
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            properties.load(reader);
            showVillagerNameTags = Boolean.parseBoolean(properties.getProperty(SHOW_VILLAGER_NAME_TAGS, "true"));
            confirmQuestAbandonment = Boolean.parseBoolean(
                    properties.getProperty(CONFIRM_QUEST_ABANDONMENT, "true"));
        } catch (IOException exception) {
            LOGGER.warn("Failed to read Villager Retaliation client preferences from {}", CONFIG_PATH, exception);
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(SHOW_VILLAGER_NAME_TAGS, Boolean.toString(showVillagerNameTags));
        properties.setProperty(CONFIRM_QUEST_ABANDONMENT, Boolean.toString(confirmQuestAbandonment));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                properties.store(writer, "Villager Retaliation client preferences");
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to save Villager Retaliation client preferences to {}", CONFIG_PATH, exception);
        }
    }
}
