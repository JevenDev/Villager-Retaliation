package com.jvn.villagerretaliation.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.toucanlib.util.ToucanRandom;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class ProfessionLootResources {
    private static final String PROFESSION_LOOT_ROOT = "profession_loot";

    private static volatile CachedProfessionLootRules cachedLootRules = CachedProfessionLootRules.empty();

    private ProfessionLootResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedLootRules = CachedProfessionLootRules.empty();
    }

    public static List<ItemStack> roll(Villager villager, LivingDropsEvent event, RandomSource random) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return List.of();
        }

        ProfessionLootRulePool pool = load(level.getServer());
        VillagerProfession profession = villager.getVillagerData().getProfession();
        List<ProfessionLootRule> rules = pool.rules().stream()
                .filter(rule -> rule.matches(profession))
                .toList();
        if (rules.isEmpty()) {
            return List.of();
        }

        LootParams params = lootParams(level, villager, event);
        List<ItemStack> drops = new ArrayList<>();
        for (ProfessionLootRule rule : rules) {
            if (!rule.chance().passes(random)) {
                continue;
            }

            LootTable table = level.getServer().reloadableRegistries().getLootTable(rule.lootTableKey());
            if (table == LootTable.EMPTY) {
                continue;
            }
            for (ItemStack stack : table.getRandomItems(params, random)) {
                if (!stack.isEmpty()) {
                    drops.add(stack);
                }
            }
        }
        return List.copyOf(drops);
    }

    private static LootParams lootParams(ServerLevel level, Villager villager, LivingDropsEvent event) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource());

        Entity attacker = event.getSource().getEntity();
        if (attacker != null) {
            builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, attacker);
        }

        Entity directAttacker = event.getSource().getDirectEntity();
        if (directAttacker != null) {
            builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, directAttacker);
        }

        if (attacker instanceof Player player) {
            builder.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
            builder.withLuck(player.getLuck());
        }

        return builder.create(LootContextParamSets.ENTITY);
    }

    private static ProfessionLootRulePool load(MinecraftServer server) {
        CachedProfessionLootRules current = cachedLootRules;
        if (current.server() == server) {
            return current.pool();
        }

        synchronized (ProfessionLootResources.class) {
            current = cachedLootRules;
            if (current.server() == server) {
                return current.pool();
            }

            ProfessionLootRulePool loadedPool = read(server);
            cachedLootRules = new CachedProfessionLootRules(server, loadedPool);
            return loadedPool;
        }
    }

    private static ProfessionLootRulePool read(MinecraftServer server) {
        Map<String, ProfessionLootRule> rules = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(PROFESSION_LOOT_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), rules));
        return new ProfessionLootRulePool(List.copyOf(rules.values()));
    }

    private static void readFile(ResourceLocation location, Resource resource, Map<String, ProfessionLootRule> rules) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (readBoolean(root, "replace", false)) {
                rules.clear();
            }
            readRules(location, root, rules);
        } catch (IOException | IllegalStateException exception) {
            // Invalid profession loot resources are ignored so one bad datapack file cannot break every drop.
        }
    }

    private static void readRules(ResourceLocation location, JsonObject root, Map<String, ProfessionLootRule> rules) {
        JsonArray entries = root.getAsJsonArray("tables");
        if (entries == null) {
            entries = root.getAsJsonArray("loot_tables");
        }
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            String id = readString(entry, "id");
            if (readBoolean(entry, "remove", false)) {
                if (!id.isBlank()) {
                    rules.remove(id);
                }
                index++;
                continue;
            }

            Optional<ResourceLocation> lootTable = parseResourceLocation(readString(entry, "loot_table"));
            if (lootTable.isEmpty()) {
                index++;
                continue;
            }

            String resolvedId = id.isBlank() ? fallbackId(location, index) : id;
            rules.put(resolvedId, new ProfessionLootRule(
                    resolvedId,
                    readProfessions(entry),
                    ResourceKey.create(Registries.LOOT_TABLE, lootTable.get()),
                    readChance(entry)
            ));
            index++;
        }
    }

    private static Set<VillagerProfession> readProfessions(JsonObject entry) {
        Set<VillagerProfession> professions = new HashSet<>();
        for (String value : readStringList(entry, "professions")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        return Set.copyOf(professions);
    }

    private static ChanceGate readChance(JsonObject entry) {
        JsonElement element = entry.get("chance");
        if (element == null || !element.isJsonPrimitive()) {
            return ConfiguredChance.ALWAYS;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return new FixedChance(Math.max(0.0D, Math.min(1.0D, primitive.getAsDouble())));
        }

        return switch (primitive.getAsString().trim().toLowerCase(Locale.ROOT)) {
            case "", "always" -> ConfiguredChance.ALWAYS;
            case "rare" -> ConfiguredChance.RARE;
            case "very_rare" -> ConfiguredChance.VERY_RARE;
            default -> ConfiguredChance.ALWAYS;
        };
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.contains(":") ? value : "minecraft:" + value;
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static List<String> readStringList(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive()) {
                continue;
            }
            String value = child.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static String fallbackId(ResourceLocation location, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_table_" + index;
    }

    private interface ChanceGate {
        boolean passes(RandomSource random);
    }

    private enum ConfiguredChance implements ChanceGate {
        ALWAYS {
            @Override
            public boolean passes(RandomSource random) {
                return true;
            }
        },
        RARE {
            @Override
            public boolean passes(RandomSource random) {
                return ToucanRandom.chance(random, VillagerRetaliationConfig.RARE_DROP_CHANCE.get());
            }
        },
        VERY_RARE {
            @Override
            public boolean passes(RandomSource random) {
                return ToucanRandom.chance(random, VillagerRetaliationConfig.VERY_RARE_DROP_CHANCE.get());
            }
        }
    }

    private record FixedChance(double chance) implements ChanceGate {
        @Override
        public boolean passes(RandomSource random) {
            return ToucanRandom.chance(random, this.chance);
        }
    }

    private record ProfessionLootRulePool(List<ProfessionLootRule> rules) {
        private static ProfessionLootRulePool empty() {
            return new ProfessionLootRulePool(List.of());
        }
    }

    private record CachedProfessionLootRules(MinecraftServer server, ProfessionLootRulePool pool) {
        private static CachedProfessionLootRules empty() {
            return new CachedProfessionLootRules(null, ProfessionLootRulePool.empty());
        }
    }

    private record ProfessionLootRule(
            String id,
            Set<VillagerProfession> professions,
            ResourceKey<LootTable> lootTableKey,
            ChanceGate chance) {
        private boolean matches(VillagerProfession profession) {
            return this.professions.isEmpty() || this.professions.contains(profession);
        }
    }
}
