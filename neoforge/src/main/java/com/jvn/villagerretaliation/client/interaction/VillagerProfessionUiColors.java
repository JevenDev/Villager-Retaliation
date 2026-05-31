package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;

public final class VillagerProfessionUiColors extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "profession_ui_colors";
    public static final ColorPair DEFAULT_COLORS = new ColorPair(0x101010, 0x323232);
    private static volatile Map<ResourceLocation, ColorPair> colorsByProfession = defaultColors();

    public VillagerProfessionUiColors() {
        super(GSON, DIRECTORY);
    }

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new VillagerProfessionUiColors());
    }

    public static ColorPair colorsFor(VillagerProfession profession) {
        return colorsByProfession.getOrDefault(VillagerProfessionUtil.id(profession), DEFAULT_COLORS);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ColorPair> loadedColors = defaultColors();
        for (Map.Entry<ResourceLocation, JsonElement> resource : resources.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList()) {
            ResourceLocation location = resource.getKey();
            if (!resource.getValue().isJsonObject()) {
                LOGGER.warn("Villager Retaliation profession UI color file {} is not an object; it will be skipped.", location);
                continue;
            }

            JsonObject root = resource.getValue().getAsJsonObject();
            if (GsonHelper.getAsBoolean(root, "replace", false)) {
                loadedColors.clear();
            }
            if (root.has("colors")) {
                for (JsonElement element : GsonHelper.getAsJsonArray(root, "colors")) {
                    if (element.isJsonObject()) {
                        readColorEntry(location, element.getAsJsonObject(), loadedColors);
                    } else {
                        LOGGER.warn("Villager Retaliation profession UI color file {} has a non-object color entry.", location);
                    }
                }
            } else {
                readColorEntry(location, root, loadedColors);
            }
        }
        colorsByProfession = Map.copyOf(loadedColors);
        LOGGER.info("Loaded {} Villager Retaliation profession UI color entries.", colorsByProfession.size());
    }

    private static void readColorEntry(ResourceLocation source, JsonObject entry, Map<ResourceLocation, ColorPair> loadedColors) {
        String professionValue = GsonHelper.getAsString(entry, "profession", "");
        ResourceLocation professionId = professionId(professionValue);
        if (professionId == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(professionId)) {
            LOGGER.warn("Villager Retaliation profession UI color file {} references unknown profession \"{}\".", source, professionValue);
            return;
        }

        int baseColor = readColor(entry, "color", -1);
        if (baseColor < 0) {
            baseColor = readColor(entry, "base_color", -1);
        }

        int darkColor = readColor(entry, "dark", baseColor < 0 ? DEFAULT_COLORS.dark() : darkened(baseColor, 0.18F));
        int lightColor = readColor(entry, "light", baseColor < 0 ? DEFAULT_COLORS.light() : darkened(baseColor, 0.34F));
        loadedColors.put(professionId, new ColorPair(darkColor, lightColor));
    }

    private static ResourceLocation professionId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if ("none".equals(normalized) || "unemployed".equals(normalized)) {
            return ResourceLocation.withDefaultNamespace("none");
        }
        return ResourceLocation.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
    }

    private static int readColor(JsonObject entry, String key, int fallback) {
        if (!entry.has(key)) {
            return fallback;
        }

        JsonElement element = entry.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() & 0xFFFFFF;
        }

        String value = GsonHelper.convertToString(element, key).trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }

        try {
            return Integer.parseUnsignedInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            LOGGER.warn("Villager Retaliation profession UI color \"{}\" is not a valid RGB color.", value);
            return fallback;
        }
    }

    private static Map<ResourceLocation, ColorPair> defaultColors() {
        Map<ResourceLocation, ColorPair> defaults = new HashMap<>();
        put(defaults, VillagerProfession.ARMORER, 0x8FA7B3);
        put(defaults, VillagerProfession.BUTCHER, 0xD64F4F);
        put(defaults, VillagerProfession.CARTOGRAPHER, 0x4FB6B8);
        put(defaults, VillagerProfession.CLERIC, 0xB967FF);
        put(defaults, VillagerProfession.FARMER, 0x7CFC00);
        put(defaults, VillagerProfession.FISHERMAN, 0x3BA7FF);
        put(defaults, VillagerProfession.FLETCHER, 0x83B547);
        put(defaults, VillagerProfession.LEATHERWORKER, 0xA86A3D);
        put(defaults, VillagerProfession.LIBRARIAN, 0xD9558F);
        put(defaults, VillagerProfession.MASON, 0x9A8F86);
        put(defaults, VillagerProfession.NITWIT, 0x6AD36A);
        put(defaults, VillagerProfession.SHEPHERD, 0xF2F2F2);
        put(defaults, VillagerProfession.TOOLSMITH, 0x6FC3D0);
        put(defaults, VillagerProfession.WEAPONSMITH, 0xFF8A2A);
        defaults.put(ResourceLocation.withDefaultNamespace("none"), DEFAULT_COLORS);
        return defaults;
    }

    private static void put(Map<ResourceLocation, ColorPair> colors, VillagerProfession profession, int baseColor) {
        colors.put(VillagerProfessionUtil.id(profession), new ColorPair(darkened(baseColor, 0.18F), darkened(baseColor, 0.34F)));
    }

    private static int darkened(int color, float factor) {
        int red = Math.round(((color >> 16) & 0xFF) * factor);
        int green = Math.round(((color >> 8) & 0xFF) * factor);
        int blue = Math.round((color & 0xFF) * factor);
        return (red << 16) | (green << 8) | blue;
    }

    public record ColorPair(int dark, int light) {
    }
}
