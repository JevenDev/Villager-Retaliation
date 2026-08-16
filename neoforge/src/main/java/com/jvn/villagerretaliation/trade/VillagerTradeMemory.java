package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

/** Persistent, per-villager shuffle-bag state for skill-trade definitions. */
public final class VillagerTradeMemory {
    private static final String MEMORY_KEY = "VillagerRetaliationTradeMemory";
    private static final String POOLS_KEY = "Pools";
    private static final String PROFESSION_KEY = "Profession";
    private static final String VERSION_KEY = "Version";
    private static final String SEED_KEY = "Seed";
    private static final String GENERATED_GAME_TIME_KEY = "GeneratedGameTime";
    private static final String KNOWN_DEFINITIONS_KEY = "KnownDefinitions"; // v1 migration
    private static final String REMAINING_DEFINITIONS_KEY = "RemainingDefinitions";
    private static final String CONSUMED_DEFINITIONS_KEY = "ConsumedDefinitions";
    private static final String LAST_DEFINITION_KEY = "LastDefinition";
    private static final String POOL_FINGERPRINT_KEY = "PoolFingerprint";
    private static final String CYCLE_KEY = "Cycle";
    private static final int CURRENT_POOL_VERSION = 2;
    private static final ResourceLocation NONE_PROFESSION = ResourceLocation.withDefaultNamespace("none");
    private static final ResourceLocation NITWIT_PROFESSION = ResourceLocation.withDefaultNamespace("nitwit");
    private static final Map<UUID, ResourceLocation> ENSURED_PROFESSION_POOLS = new HashMap<>();

    private VillagerTradeMemory() {
    }

    public static Optional<ProfessionTradePool> ensureProfessionPool(ServerLevel level, Villager villager) {
        return ensureProfessionPool(level, villager, VillagerProfessionUtil.id(villager.getVillagerData().getProfession()));
    }

    public static void ensureProfessionPoolIfNeeded(ServerLevel level, Villager villager) {
        ResourceLocation professionId = VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
        UUID villagerId = villager.getUUID();
        if (professionId.equals(ENSURED_PROFESSION_POOLS.get(villagerId))) {
            return;
        }
        if (!isTradeProfession(professionId)) {
            ENSURED_PROFESSION_POOLS.put(villagerId, professionId);
            return;
        }
        if (ensureProfessionPool(level, villager, professionId).isPresent()) {
            ENSURED_PROFESSION_POOLS.put(villagerId, professionId);
        }
    }

    public static void clearRuntimeState() {
        ENSURED_PROFESSION_POOLS.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        ENSURED_PROFESSION_POOLS.remove(villager.getUUID());
    }

    public static Optional<ProfessionTradePool> ensureProfessionPool(
            ServerLevel level, Villager villager, ResourceLocation professionId) {
        if (!isTradeProfession(professionId)) {
            return Optional.empty();
        }
        CompoundTag pool = mutablePool(level, villager, professionId);
        return Optional.of(readPool(pool));
    }

    /**
     * Returns the current shuffle bag in weighted random order without consuming it. Reconciliation may update
     * persisted pool membership, but failed feasibility probes never consume a definition.
     */
    public static List<ResourceLocation> cycleCandidates(
            ServerLevel level,
            Villager villager,
            ResourceLocation professionId,
            List<SkillTradeDefinition> eligibleDefinitions,
            Set<ResourceLocation> reservedDefinitionIds) {
        if (!isTradeProfession(professionId) || eligibleDefinitions.isEmpty()) {
            return List.of();
        }

        Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        eligibleDefinitions.stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .forEach(definition -> weights.put(definition.id(), definition.weight()));
        CompoundTag pool = mutablePool(level, villager, professionId);
        reconcilePool(pool, weights);

        LinkedHashSet<ResourceLocation> remaining = readLocations(pool, REMAINING_DEFINITIONS_KEY);
        Set<ResourceLocation> reserved = reservedDefinitionIds == null ? Set.of() : reservedDefinitionIds;
        List<ResourceLocation> candidates = remaining.stream()
                .filter(id -> !reserved.contains(id))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        ResourceLocation last = ResourceLocation.tryParse(pool.getString(LAST_DEFINITION_KEY));
        long selectionSeed = pool.getLong(SEED_KEY)
                ^ Long.rotateLeft(pool.getLong(CYCLE_KEY), 17)
                ^ Integer.toUnsignedLong(pool.getString(POOL_FINGERPRINT_KEY).hashCode());
        List<ResourceLocation> ordered = weightedOrder(candidates, weights, RandomSource.create(selectionSeed));
        if (remaining.size() == weights.size() && ordered.size() > 1 && ordered.getFirst().equals(last)) {
            ResourceLocation first = ordered.removeFirst();
            ordered.add(1, first);
        }
        return List.copyOf(ordered);
    }

    public static void consumeDefinition(
            ServerLevel level,
            Villager villager,
            ResourceLocation professionId,
            ResourceLocation definitionId,
            List<SkillTradeDefinition> eligibleDefinitions) {
        if (definitionId == null || !isTradeProfession(professionId)) {
            return;
        }
        Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        eligibleDefinitions.stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .forEach(definition -> weights.put(definition.id(), definition.weight()));
        CompoundTag pool = mutablePool(level, villager, professionId);
        reconcilePool(pool, weights);
        LinkedHashSet<ResourceLocation> remaining = readLocations(pool, REMAINING_DEFINITIONS_KEY);
        LinkedHashSet<ResourceLocation> consumed = readLocations(pool, CONSUMED_DEFINITIONS_KEY);
        remaining.remove(definitionId);
        if (weights.containsKey(definitionId)) {
            consumed.add(definitionId);
            pool.putString(LAST_DEFINITION_KEY, definitionId.toString());
        }
        writeLocations(pool, REMAINING_DEFINITIONS_KEY, remaining);
        writeLocations(pool, CONSUMED_DEFINITIONS_KEY, consumed);
    }

    /** Compatibility entry point used by natural generation and Special Orders. */
    public static void rememberDefinition(
            ServerLevel level, Villager villager, ResourceLocation professionId, ResourceLocation definitionId) {
        List<SkillTradeDefinition> eligible = SkillTradeResources.definitions(level.getServer()).stream()
                .filter(definition -> definition.matchesVillagerAtOrBelow(
                        professionId, villager.getVillagerData().getLevel()))
                .filter(definition -> definition.conditions().matches())
                .toList();
        consumeDefinition(level, villager, professionId, definitionId, eligible);
    }

    /** Compatibility view for diagnostics and migration tests. */
    public static List<ResourceLocation> knownDefinitionIds(Villager villager, ResourceLocation professionId) {
        if (!isTradeProfession(professionId)) {
            return List.of();
        }
        Optional<CompoundTag> pool = existingPool(villager, professionId);
        if (pool.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<ResourceLocation> known = readLocations(pool.get(), CONSUMED_DEFINITIONS_KEY);
        known.addAll(readLocations(pool.get(), KNOWN_DEFINITIONS_KEY));
        return List.copyOf(known);
    }

    private static void reconcilePool(CompoundTag pool, Map<ResourceLocation, Integer> weights) {
        LinkedHashSet<ResourceLocation> eligible = new LinkedHashSet<>(weights.keySet());
        LinkedHashSet<ResourceLocation> remaining;
        LinkedHashSet<ResourceLocation> consumed;
        if (pool.getInt(VERSION_KEY) < CURRENT_POOL_VERSION) {
            consumed = readLocations(pool, KNOWN_DEFINITIONS_KEY);
            consumed.retainAll(eligible);
            remaining = new LinkedHashSet<>(eligible);
            remaining.removeAll(consumed);
            List<ResourceLocation> legacy = new ArrayList<>(consumed);
            if (!legacy.isEmpty()) {
                pool.putString(LAST_DEFINITION_KEY, legacy.getLast().toString());
            }
            pool.remove(KNOWN_DEFINITIONS_KEY);
        } else {
            remaining = readLocations(pool, REMAINING_DEFINITIONS_KEY);
            consumed = readLocations(pool, CONSUMED_DEFINITIONS_KEY);
            remaining.retainAll(eligible);
            consumed.retainAll(eligible);
            for (ResourceLocation id : eligible) {
                if (!remaining.contains(id) && !consumed.contains(id)) {
                    remaining.add(id);
                }
            }
        }

        if (remaining.isEmpty() && !eligible.isEmpty()) {
            remaining.addAll(eligible);
            consumed.clear();
            pool.putLong(CYCLE_KEY, pool.getLong(CYCLE_KEY) + 1L);
        }
        pool.putInt(VERSION_KEY, CURRENT_POOL_VERSION);
        pool.putString(POOL_FINGERPRINT_KEY, fingerprint(weights));
        writeLocations(pool, REMAINING_DEFINITIONS_KEY, remaining);
        writeLocations(pool, CONSUMED_DEFINITIONS_KEY, consumed);
    }

    private static List<ResourceLocation> weightedOrder(
            List<ResourceLocation> values, Map<ResourceLocation, Integer> weights, RandomSource random) {
        List<ResourceLocation> remaining = new ArrayList<>(values);
        List<ResourceLocation> ordered = new ArrayList<>(values.size());
        while (!remaining.isEmpty()) {
            int total = remaining.stream().mapToInt(id -> Math.max(1, weights.getOrDefault(id, 1))).sum();
            int roll = random.nextInt(Math.max(1, total));
            ResourceLocation selected = remaining.getLast();
            for (ResourceLocation id : remaining) {
                roll -= Math.max(1, weights.getOrDefault(id, 1));
                if (roll < 0) {
                    selected = id;
                    break;
                }
            }
            ordered.add(selected);
            remaining.remove(selected);
        }
        return ordered;
    }

    private static String fingerprint(Map<ResourceLocation, Integer> weights) {
        String value = weights.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        return Integer.toUnsignedString(value.hashCode(), 16);
    }

    private static boolean isTradeProfession(ResourceLocation professionId) {
        return professionId != null && !NONE_PROFESSION.equals(professionId) && !NITWIT_PROFESSION.equals(professionId);
    }

    private static CompoundTag mutablePool(ServerLevel level, Villager villager, ResourceLocation professionId) {
        CompoundTag persistentData = villager.getPersistentData();
        CompoundTag memory = persistentData.contains(MEMORY_KEY, Tag.TAG_COMPOUND)
                ? persistentData.getCompound(MEMORY_KEY) : new CompoundTag();
        ListTag pools = memory.contains(POOLS_KEY, Tag.TAG_LIST)
                ? memory.getList(POOLS_KEY, Tag.TAG_COMPOUND) : new ListTag();
        CompoundTag pool = poolTag(pools, professionId).orElseGet(() -> createPoolTag(level, villager, professionId, pools));
        memory.put(POOLS_KEY, pools);
        persistentData.put(MEMORY_KEY, memory);
        return pool;
    }

    private static Optional<CompoundTag> existingPool(Villager villager, ResourceLocation professionId) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(MEMORY_KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag memory = data.getCompound(MEMORY_KEY);
        if (!memory.contains(POOLS_KEY, Tag.TAG_LIST)) return Optional.empty();
        return poolTag(memory.getList(POOLS_KEY, Tag.TAG_COMPOUND), professionId);
    }

    private static Optional<CompoundTag> poolTag(ListTag pools, ResourceLocation professionId) {
        for (int i = 0; i < pools.size(); i++) {
            CompoundTag pool = pools.getCompound(i);
            if (professionId.toString().equals(pool.getString(PROFESSION_KEY))) return Optional.of(pool);
        }
        return Optional.empty();
    }

    private static CompoundTag createPoolTag(ServerLevel level, Villager villager, ResourceLocation professionId, ListTag pools) {
        CompoundTag pool = new CompoundTag();
        pool.putString(PROFESSION_KEY, professionId.toString());
        pool.putInt(VERSION_KEY, CURRENT_POOL_VERSION);
        pool.putLong(SEED_KEY, villager.getRandom().nextLong());
        pool.putLong(GENERATED_GAME_TIME_KEY, level.getGameTime());
        pool.putLong(CYCLE_KEY, 0L);
        pool.put(REMAINING_DEFINITIONS_KEY, new ListTag());
        pool.put(CONSUMED_DEFINITIONS_KEY, new ListTag());
        pools.add(pool);
        return pool;
    }

    private static LinkedHashSet<ResourceLocation> readLocations(CompoundTag pool, String key) {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        if (!pool.contains(key, Tag.TAG_LIST)) return ids;
        ListTag values = pool.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < values.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(values.getString(i));
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static void writeLocations(CompoundTag pool, String key, Set<ResourceLocation> ids) {
        ListTag values = new ListTag();
        ids.forEach(id -> values.add(StringTag.valueOf(id.toString())));
        pool.put(key, values);
    }

    private static ProfessionTradePool readPool(CompoundTag pool) {
        ResourceLocation professionId = ResourceLocation.tryParse(pool.getString(PROFESSION_KEY));
        if (professionId == null) professionId = NONE_PROFESSION;
        return new ProfessionTradePool(
                professionId,
                Math.max(0, pool.getInt(VERSION_KEY)),
                pool.getLong(SEED_KEY),
                pool.getLong(GENERATED_GAME_TIME_KEY),
                List.copyOf(readLocations(pool, CONSUMED_DEFINITIONS_KEY)));
    }

    public record ProfessionTradePool(
            ResourceLocation professionId,
            int version,
            long seed,
            long generatedGameTime,
            List<ResourceLocation> knownDefinitionIds) {
        public ProfessionTradePool {
            knownDefinitionIds = knownDefinitionIds == null ? List.of() : List.copyOf(knownDefinitionIds);
        }
    }
}
