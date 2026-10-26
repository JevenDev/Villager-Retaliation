package com.jvn.villagerretaliation.diplomacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

public final class VillageDiplomacyRules {
    private static ResourceManager resources;
    private static Rules cached;
    private static String problem = "";
    public static final Rules DEFAULT = new Rules(true, 1200, 1024, 2048, 20, 60, 5, 72000, 3, 200, 2,
            24000, 120000, 32, 8, 100, 4096, 256, 40);

    private VillageDiplomacyRules() { }

    public record Rules(boolean enabled, int cadence, int contactRange, int shipmentRange,
                        int friendlyThreshold, int trustedThreshold, int standingBuffer,
                        int aidCooldown, int aidStandingLimit, int letterMinimum, int letterTicksPerBlock,
                        int shortageDuration, int requestLifetime, int activeJourneys, int physicalCouriers,
                        int generationInterval, int routeChunkLimit, int generationLimit, int maximumTickMillis) { }

    public static Rules get(MinecraftServer server) {
        if (resources == server.getResourceManager() && cached != null) return cached;
        resources = server.getResourceManager();
        try (var reader = resources.getResourceOrThrow(ResourceLocation.parse("villagerretaliation:diplomacy/default.json")).openAsReader()) {
            cached = parse(JsonParser.parseReader(reader).getAsJsonObject());
            problem = "";
        } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException | ArithmeticException failure) {
            problem = "Invalid diplomacy rules";
            LogUtils.getLogger().error("Invalid diplomacy rules, diplomatic decisions suspended", failure);
            cached = DEFAULT;
        }
        return cached;
    }

    public static String problem() { return problem; }

    static Rules parse(JsonObject json) {
        if (!json.has("enabled") || !json.get("enabled").isJsonPrimitive()
                || !json.getAsJsonPrimitive("enabled").isBoolean()) throw new IllegalArgumentException("enabled");
        var rules = new Rules(json.get("enabled").getAsBoolean(), value(json, "cadence_ticks", 200, 24000),
                value(json, "contact_range", 128, 8192), value(json, "shipment_range", 128, 16384),
                value(json, "friendly_threshold", 1, 80), value(json, "trusted_threshold", 2, 100),
                value(json, "standing_buffer", 0, 20), value(json, "aid_cooldown_ticks", 24000, 2400000),
                value(json, "aid_standing_limit", 1, 10), value(json, "letter_minimum_ticks", 20, 24000),
                value(json, "letter_ticks_per_block", 1, 100), value(json, "shortage_duration_ticks", 1200, 240000),
                value(json, "request_lifetime_ticks", 24000, 2400000), value(json, "active_journeys", 1, 128),
                value(json, "physical_couriers", 1, 32), value(json, "generation_interval_ticks", 100, 24000),
                value(json, "route_chunk_limit", 64, 16384), value(json, "generation_limit", 0, 1024),
                value(json, "maximum_tick_millis", 10, 45));
        if (rules.trustedThreshold() <= rules.friendlyThreshold() + rules.standingBuffer()
                || rules.standingBuffer() >= rules.friendlyThreshold()
                || rules.physicalCouriers() > rules.activeJourneys()) throw new IllegalArgumentException("Conflicting diplomacy limits");
        return rules;
    }

    private static int value(JsonObject json, String name, int minimum, int maximum) {
        var raw = json.get(name);
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(name);
        int result = raw.getAsBigDecimal().intValueExact();
        if (result < minimum || result > maximum) throw new IllegalArgumentException(name);
        return result;
    }
}
