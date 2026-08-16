package com.jvn.villagerretaliation.raid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Datapack API for profession-filtered player-raid militia loadouts. */
public final class PlayerRaidLoadoutResources {
    public static final String RESOURCE_ROOT = "player_raid_loadouts";
    private static volatile Cached cached = new Cached(null, List.of());

    private PlayerRaidLoadoutResources() {
    }

    public static void warm(MinecraftServer server) {
        profiles(server);
    }

    public static void clearCache() {
        cached = new Cached(null, List.of());
    }

    public static Optional<LoadoutProfile> profile(MinecraftServer server, VillagerProfession profession) {
        return profiles(server).stream().filter(profile -> profile.matches(profession)).findFirst();
    }

    private static List<LoadoutProfile> profiles(MinecraftServer server) {
        Cached current = cached;
        if (current.server == server) return current.profiles;
        synchronized (PlayerRaidLoadoutResources.class) {
            if (cached.server == server) return cached.profiles;
            Map<String, LoadoutProfile> profiles = new LinkedHashMap<>();
            DatapackResourceLoader.forEachJsonResource(
                    server, RESOURCE_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID),
                    (location, resource) -> readFile(location, resource, profiles));
            cached = new Cached(server, List.copyOf(profiles.values()));
            return cached.profiles;
        }
    }

    private static void readFile(ResourceLocation location, Resource resource, Map<String, LoadoutProfile> profiles) {
        DatapackResourceLoader.readObject(location, "player raid loadouts", resource).ifPresent(root -> {
            if (booleanValue(root, "replace", false)) profiles.clear();
            JsonArray entries = root.getAsJsonArray("loadouts");
            if (entries == null) return;
            int index = 0;
            for (JsonElement raw : entries) {
                if (!raw.isJsonObject()) { index++; continue; }
                JsonObject entry = raw.getAsJsonObject();
                String id = string(entry, "id", location + "#" + index);
                if (booleanValue(entry, "remove", false)) { profiles.remove(id); index++; continue; }
                List<VillagerProfession> included = professions(entry.getAsJsonArray("professions"));
                List<VillagerProfession> excluded = professions(entry.getAsJsonArray("excluded_professions"));
                EnumMap<Difficulty, DifficultyPool> pools = new EnumMap<>(Difficulty.class);
                JsonObject poolObject = entry.getAsJsonObject("difficulty_pools");
                if (poolObject != null) {
                    for (Difficulty difficulty : Difficulty.values()) {
                        JsonObject pool = poolObject.getAsJsonObject(difficulty.getKey());
                        if (pool != null) pools.put(difficulty, readPool(pool));
                    }
                }
                if (!pools.isEmpty()) profiles.put(id, new LoadoutProfile(id, included, excluded, pools));
                index++;
            }
        });
    }

    private static DifficultyPool readPool(JsonObject object) {
        List<Item> weapons = items(object.getAsJsonArray("weapons"));
        float armorChance = (float) doubleValue(object, "armor_chance", 0.0D);
        float enchantChance = (float) doubleValue(object, "enchant_chance", 0.0D);
        List<WeightedArmorSet> armor = new ArrayList<>();
        JsonArray sets = object.getAsJsonArray("armor_sets");
        if (sets != null) for (JsonElement raw : sets) {
            if (!raw.isJsonObject()) continue;
            JsonObject set = raw.getAsJsonObject();
            armor.add(new WeightedArmorSet(
                    Math.max(0, (int) doubleValue(set, "weight", 1.0D)),
                    item(set, "head"), item(set, "chest"), item(set, "legs"), item(set, "feet")));
        }
        return new DifficultyPool(
                weapons,
                Math.max(0.0F, Math.min(1.0F, armorChance)),
                Math.max(0.0F, Math.min(1.0F, enchantChance)),
                armor);
    }

    private static List<VillagerProfession> professions(JsonArray array) {
        if (array == null) return List.of();
        List<VillagerProfession> result = new ArrayList<>();
        for (JsonElement raw : array) if (raw.isJsonPrimitive()) {
            VillagerProfessionUtil.parse(raw.getAsString()).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static List<Item> items(JsonArray array) {
        if (array == null) return List.of();
        List<Item> result = new ArrayList<>();
        for (JsonElement raw : array) if (raw.isJsonPrimitive()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(raw.getAsString()));
            if (item != Items.AIR) result.add(item);
        }
        return List.copyOf(result);
    }

    private static Item item(JsonObject object, String key) {
        String value = string(object, key, "minecraft:air");
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(value));
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }

    public record LoadoutProfile(
            String id, List<VillagerProfession> professions, List<VillagerProfession> excludedProfessions,
            Map<Difficulty, DifficultyPool> difficultyPools) {
        public boolean matches(VillagerProfession profession) {
            return !this.excludedProfessions.contains(profession)
                    && (this.professions.isEmpty() || this.professions.contains(profession));
        }

        public DifficultyPool pool(Difficulty difficulty) {
            DifficultyPool pool = this.difficultyPools.get(difficulty);
            return pool != null ? pool : this.difficultyPools.values().stream().findFirst().orElse(DifficultyPool.EMPTY);
        }
    }

    public record DifficultyPool(
            List<Item> weapons, float armorChance, float enchantChance, List<WeightedArmorSet> armorSets) {
        private static final DifficultyPool EMPTY = new DifficultyPool(List.of(), 0.0F, 0.0F, List.of());

        public Item weapon(RandomSource random) {
            return this.weapons.isEmpty() ? Items.AIR : this.weapons.get(random.nextInt(this.weapons.size()));
        }

        public WeightedArmorSet armor(RandomSource random) {
            int total = this.armorSets.stream().mapToInt(WeightedArmorSet::weight).sum();
            if (total <= 0) return null;
            int selected = random.nextInt(total);
            for (WeightedArmorSet set : this.armorSets) {
                selected -= set.weight();
                if (selected < 0) return set;
            }
            return null;
        }
    }

    public record WeightedArmorSet(int weight, Item head, Item chest, Item legs, Item feet) {
        public Item item(EquipmentSlot slot) {
            return switch (slot) {
                case HEAD -> this.head;
                case CHEST -> this.chest;
                case LEGS -> this.legs;
                case FEET -> this.feet;
                default -> Items.AIR;
            };
        }
    }

    private record Cached(MinecraftServer server, List<LoadoutProfile> profiles) {
    }
}
