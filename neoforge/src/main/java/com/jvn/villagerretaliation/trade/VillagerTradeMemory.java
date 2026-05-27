package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerTradeMemory {
    private static final String MEMORY_KEY = "VillagerRetaliationTradeMemory";
    private static final String POOLS_KEY = "Pools";
    private static final String PROFESSION_KEY = "Profession";
    private static final String VERSION_KEY = "Version";
    private static final String SEED_KEY = "Seed";
    private static final String GENERATED_GAME_TIME_KEY = "GeneratedGameTime";
    private static final String KNOWN_DEFINITIONS_KEY = "KnownDefinitions";
    private static final int CURRENT_POOL_VERSION = 1;
    private static final ResourceLocation NONE_PROFESSION = ResourceLocation.withDefaultNamespace("none");
    private static final ResourceLocation NITWIT_PROFESSION = ResourceLocation.withDefaultNamespace("nitwit");

    private VillagerTradeMemory() {
    }

    public static Optional<ProfessionTradePool> ensureProfessionPool(ServerLevel level, Villager villager) {
        return ensureProfessionPool(level, villager, VillagerProfessionUtil.id(villager.getVillagerData().getProfession()));
    }

    public static Optional<ProfessionTradePool> ensureProfessionPool(
            ServerLevel level,
            Villager villager,
            ResourceLocation professionId) {
        if (!isTradeProfession(professionId)) {
            return Optional.empty();
        }

        CompoundTag persistentData = villager.getPersistentData();
        CompoundTag memory = memoryTag(persistentData);
        ListTag pools = poolsTag(memory);
        CompoundTag pool = poolTag(pools, professionId).orElseGet(() -> createPoolTag(level, villager, professionId, pools));
        memory.put(POOLS_KEY, pools);
        persistentData.put(MEMORY_KEY, memory);
        return Optional.of(readPool(pool));
    }

    public static List<ResourceLocation> knownDefinitionIds(Villager villager, ResourceLocation professionId) {
        if (!isTradeProfession(professionId)) {
            return List.of();
        }

        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(MEMORY_KEY, Tag.TAG_COMPOUND)) {
            return List.of();
        }

        CompoundTag memory = persistentData.getCompound(MEMORY_KEY);
        if (!memory.contains(POOLS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        return poolTag(memory.getList(POOLS_KEY, Tag.TAG_COMPOUND), professionId)
                .map(VillagerTradeMemory::readKnownDefinitions)
                .orElse(List.of());
    }

    public static void rememberDefinition(
            ServerLevel level,
            Villager villager,
            ResourceLocation professionId,
            ResourceLocation definitionId) {
        if (!isTradeProfession(professionId) || definitionId == null) {
            return;
        }

        CompoundTag persistentData = villager.getPersistentData();
        CompoundTag memory = memoryTag(persistentData);
        ListTag pools = poolsTag(memory);
        CompoundTag pool = poolTag(pools, professionId).orElseGet(() -> createPoolTag(level, villager, professionId, pools));
        ListTag known = pool.contains(KNOWN_DEFINITIONS_KEY, Tag.TAG_LIST)
                ? pool.getList(KNOWN_DEFINITIONS_KEY, Tag.TAG_STRING)
                : new ListTag();
        String serialized = definitionId.toString();
        for (int i = 0; i < known.size(); i++) {
            if (serialized.equals(known.getString(i))) {
                return;
            }
        }

        known.add(StringTag.valueOf(serialized));
        pool.put(KNOWN_DEFINITIONS_KEY, known);
        memory.put(POOLS_KEY, pools);
        persistentData.put(MEMORY_KEY, memory);
    }

    private static boolean isTradeProfession(ResourceLocation professionId) {
        return professionId != null
                && !NONE_PROFESSION.equals(professionId)
                && !NITWIT_PROFESSION.equals(professionId);
    }

    private static CompoundTag memoryTag(CompoundTag persistentData) {
        return persistentData.contains(MEMORY_KEY, Tag.TAG_COMPOUND)
                ? persistentData.getCompound(MEMORY_KEY)
                : new CompoundTag();
    }

    private static ListTag poolsTag(CompoundTag memory) {
        return memory.contains(POOLS_KEY, Tag.TAG_LIST)
                ? memory.getList(POOLS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
    }

    private static Optional<CompoundTag> poolTag(ListTag pools, ResourceLocation professionId) {
        String serialized = professionId.toString();
        for (int i = 0; i < pools.size(); i++) {
            CompoundTag pool = pools.getCompound(i);
            if (serialized.equals(pool.getString(PROFESSION_KEY))) {
                return Optional.of(pool);
            }
        }
        return Optional.empty();
    }

    private static CompoundTag createPoolTag(
            ServerLevel level,
            Villager villager,
            ResourceLocation professionId,
            ListTag pools) {
        CompoundTag pool = new CompoundTag();
        pool.putString(PROFESSION_KEY, professionId.toString());
        pool.putInt(VERSION_KEY, CURRENT_POOL_VERSION);
        pool.putLong(SEED_KEY, villager.getRandom().nextLong());
        pool.putLong(GENERATED_GAME_TIME_KEY, level.getGameTime());
        pool.put(KNOWN_DEFINITIONS_KEY, new ListTag());
        pools.add(pool);
        return pool;
    }

    private static ProfessionTradePool readPool(CompoundTag pool) {
        ResourceLocation professionId = ResourceLocation.tryParse(pool.getString(PROFESSION_KEY));
        if (professionId == null) {
            professionId = NONE_PROFESSION;
        }
        return new ProfessionTradePool(
                professionId,
                Math.max(0, pool.getInt(VERSION_KEY)),
                pool.getLong(SEED_KEY),
                pool.getLong(GENERATED_GAME_TIME_KEY),
                readKnownDefinitions(pool));
    }

    private static List<ResourceLocation> readKnownDefinitions(CompoundTag pool) {
        if (!pool.contains(KNOWN_DEFINITIONS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag known = pool.getList(KNOWN_DEFINITIONS_KEY, Tag.TAG_STRING);
        List<ResourceLocation> ids = new ArrayList<>(known.size());
        for (int i = 0; i < known.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(known.getString(i));
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
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
