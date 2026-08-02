package com.jvn.villagerretaliation.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class BetaWarningState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path STATE_PATH =
            FMLPaths.CONFIGDIR.get().resolve("villagerretaliation_beta_13_warning.json");
    private static boolean loaded;
    private static boolean acknowledged;

    private BetaWarningState() {}

    public static synchronized boolean isAcknowledged() {
        if (!loaded) {
            load();
        }
        return acknowledged;
    }

    public static synchronized void acknowledge() {
        if (isAcknowledged()) {
            return;
        }
        acknowledged = true;
        save();
    }

    private static void load() {
        loaded = true;
        acknowledged = false;
        if (!Files.isRegularFile(STATE_PATH)) {
            return;
        }

        try {
            JsonElement element = JsonParser.parseString(Files.readString(STATE_PATH, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return;
            }
            JsonElement acknowledgedValue = element.getAsJsonObject().get("acknowledged");
            if (acknowledgedValue != null
                    && acknowledgedValue.isJsonPrimitive()
                    && acknowledgedValue.getAsJsonPrimitive().isBoolean()) {
                acknowledged = acknowledgedValue.getAsBoolean();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load Villager Retaliation Beta.13 warning state from {}", STATE_PATH, exception);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(STATE_PATH.getParent());
            JsonObject object = new JsonObject();
            object.addProperty("format", "villagerretaliation_beta_13_warning_v1");
            object.addProperty("acknowledged", acknowledged);
            Files.writeString(STATE_PATH, object.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Failed to save Villager Retaliation Beta.13 warning state to {}", STATE_PATH, exception);
        }
    }
}
