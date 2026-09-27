package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class SettlementStageRules {
    public static final int MAX_RADIUS = 512;
    public static final int MAX_PROJECTS = 64;
    public static final int MAX_DEPOTS = 512;
    public static final int MAX_MARKET_CAPACITY = 65_536;
    private static final Set<String> KEYS = Set.of("radius", "projects", "depots", "market_supply", "market_demand", "services");
    private static final Set<SettlementProgressionService.Requirement> SERVICES = EnumSet.of(
            SettlementProgressionService.Requirement.FOOD_PRODUCTION, SettlementProgressionService.Requirement.KITCHEN,
            SettlementProgressionService.Requirement.GUARD_POST, SettlementProgressionService.Requirement.WORKSHOP,
            SettlementProgressionService.Requirement.TRADING_POST);
    private static final Map<SettlementStage, Limits> DEFAULTS = defaults();
    private static volatile Map<SettlementStage, Limits> rules = DEFAULTS;

    private SettlementStageRules() {
    }

    public static Limits forStage(SettlementStage stage) {
        return rules.get(stage);
    }

    static Limits legacy(SettlementStage stage) {
        return DEFAULTS.get(stage);
    }

    public static void reload(ResourceManager resources) {
        var loaded = new EnumMap<SettlementStage, Limits>(SettlementStage.class);
        for (var stage : SettlementStage.values()) {
            var location = ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID,
                    "settlement_stages/" + stage.name().toLowerCase(Locale.ROOT) + ".json");
            try (var reader = resources.getResourceOrThrow(location).openAsReader()) {
                var root = GsonHelper.parse(reader);
                DatapackDiagnostics.warnUnknownRootKeys(location, "settlement stages", root, KEYS);
                loaded.put(stage, parse(root));
            } catch (IOException | JsonParseException | IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid settlement stage rules at " + location, exception);
            }
        }
        apply(loaded);
    }

    static Limits parse(JsonObject root) {
        var services = EnumSet.noneOf(SettlementProgressionService.Requirement.class);
        for (var entry : GsonHelper.getAsJsonArray(root, "services")) {
            var requirement = SettlementProgressionService.Requirement.valueOf(
                    GsonHelper.convertToString(entry, "service").toUpperCase(Locale.ROOT));
            if (!services.add(requirement)) throw new IllegalArgumentException("Duplicate settlement service " + requirement);
        }
        return new Limits(integer(root, "radius"), integer(root, "projects"), integer(root, "depots"),
                integer(root, "market_supply"), integer(root, "market_demand"), services);
    }

    private static int integer(JsonObject root, String key) {
        var value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonSyntaxException("Settlement stage " + key + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new JsonSyntaxException("Settlement stage " + key + " must be an integer", exception);
        }
    }

    static void apply(Map<SettlementStage, Limits> values) {
        if (values.size() != SettlementStage.values().length) throw new IllegalArgumentException("Every settlement stage needs rules");
        Limits previous = null;
        for (var stage : SettlementStage.values()) {
            var value = values.get(stage);
            if (value == null) throw new IllegalArgumentException("Missing settlement stage " + stage);
            if (previous != null && (value.radius() < previous.radius() || value.projects() < previous.projects()
                    || value.depots() < previous.depots() || value.marketSupply() < previous.marketSupply()
                    || value.marketDemand() < previous.marketDemand())) {
                throw new IllegalArgumentException("Settlement capacities cannot decrease at " + stage);
            }
            if (stage == SettlementStage.CAMP && value.projects() != 0
                    || stage.ordinal() < SettlementStage.VILLAGE.ordinal() && (value.marketSupply() != 0 || value.marketDemand() != 0)
                    || stage.ordinal() <= SettlementStage.VILLAGE.ordinal()
                    && value.services().contains(SettlementProgressionService.Requirement.TRADING_POST)) {
                throw new IllegalArgumentException("Settlement rules require a capability before it unlocks at " + stage);
            }
            previous = value;
        }
        var prior = rules;
        rules = Map.copyOf(values);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.overworld() != null) {
            for (var town : SettlementSavedData.get(server.overworld()).settlements()) {
                var target = town.stage().next().orElse(town.stage());
                town.refreshStageRules(!prior.get(target).services().equals(rules.get(target).services()));
            }
        }
    }

    private static Map<SettlementStage, Limits> defaults() {
        var defaults = new EnumMap<SettlementStage, Limits>(SettlementStage.class);
        var services = EnumSet.noneOf(SettlementProgressionService.Requirement.class);
        for (var stage : SettlementStage.values()) {
            switch (stage) {
                case HAMLET -> services.add(SettlementProgressionService.Requirement.FOOD_PRODUCTION);
                case VILLAGE -> services.add(SettlementProgressionService.Requirement.KITCHEN);
                case TOWN -> services.add(SettlementProgressionService.Requirement.GUARD_POST);
                case CITY -> {
                    services.add(SettlementProgressionService.Requirement.WORKSHOP);
                    services.add(SettlementProgressionService.Requirement.TRADING_POST);
                }
                default -> {
                }
            }
            int projects = stage == SettlementStage.CAMP ? 0 : 1 << (stage.ordinal() - 1);
            int supply = stage.ordinal() < SettlementStage.VILLAGE.ordinal() ? 0 : 16 << stage.ordinal();
            defaults.put(stage, new Limits(stage.legacyRadius(), projects, 8 << stage.ordinal(), supply, supply * 2, services));
        }
        return Map.copyOf(defaults);
    }

    public record Limits(int radius, int projects, int depots, int marketSupply, int marketDemand,
                         Set<SettlementProgressionService.Requirement> services) {
        public Limits {
            services = Set.copyOf(services);
            if (radius < 16 || radius > MAX_RADIUS || projects < 0 || projects > MAX_PROJECTS
                    || depots < 1 || depots > MAX_DEPOTS || marketSupply < 0 || marketSupply > MAX_MARKET_CAPACITY
                    || marketDemand < 0 || marketDemand > MAX_MARKET_CAPACITY || !SERVICES.containsAll(services)) {
                throw new IllegalArgumentException("Settlement stage rules exceed their limits or contain a non-service requirement");
            }
        }
    }
}
