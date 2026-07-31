package com.jvn.villagerretaliation.interaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

public final class HiredVillagerIndex {
    public static final int MAX_ASSIGNMENTS_PER_PLAYER = 64;
    private static final Map<UUID, Entry> BY_VILLAGER = new HashMap<>();
    private static final Map<UUID, LinkedHashSet<UUID>> BY_OWNER = new HashMap<>();

    private HiredVillagerIndex() {
    }

    public static void update(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !villager.isAlive() || villager.isBaby()) {
            removeIfIndexed(villager);
            return;
        }
        if (!HiredVillagerContractService.hasContract(villager)) {
            removeIfIndexed(villager);
            return;
        }
        Optional<UUID> owner = HiredVillagerContractService.getHirer(level, villager);
        if (owner.isEmpty()) {
            removeIfIndexed(villager);
            return;
        }
        upsert(level, villager, owner.get());
    }

    public static Optional<Target> find(ServerPlayer player, UUID villagerId) {
        if (player == null || player.server == null || villagerId == null) {
            return Optional.empty();
        }
        Entry entry = BY_VILLAGER.get(villagerId);
        if (entry == null || !entry.owner().equals(player.getUUID())) {
            return Optional.empty();
        }
        return resolve(player.server, villagerId, entry);
    }

    public static List<Target> targetsFor(ServerPlayer player) {
        if (player == null || player.server == null) {
            return List.of();
        }
        Set<UUID> indexed = BY_OWNER.get(player.getUUID());
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }

        List<Target> targets = new ArrayList<>();
        for (UUID villagerId : List.copyOf(indexed)) {
            Entry entry = BY_VILLAGER.get(villagerId);
            if (entry == null || !entry.owner().equals(player.getUUID())) {
                remove(villagerId);
                continue;
            }
            resolve(player.server, villagerId, entry).ifPresent(targets::add);
        }
        return targets;
    }

    public static void reconcileLoadedFor(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        UUID ownerId = player.getUUID();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Villager villager)
                        || !villager.isAlive()
                        || villager.isBaby()
                        || !HiredVillagerContractService.hasContract(villager)) {
                    continue;
                }
                Optional<UUID> owner = HiredVillagerContractService.getHirer(level, villager);
                if (owner.isPresent() && owner.get().equals(ownerId)) {
                    upsert(level, villager, ownerId);
                }
            }
        }
    }

    public static void remove(Villager villager) {
        if (villager != null) {
            remove(villager.getUUID());
        }
    }

    public static void clearRuntimeState() {
        BY_VILLAGER.clear();
        BY_OWNER.clear();
    }


    private static void upsert(ServerLevel level, Villager villager, UUID owner) {
        UUID villagerId = villager.getUUID();
        Entry previous = BY_VILLAGER.get(villagerId);
        if (previous != null && previous.owner().equals(owner) && previous.dimension().equals(level.dimension())) {
            Set<UUID> owned = BY_OWNER.get(owner);
            if (owned != null && owned.contains(villagerId)) {
                return;
            }
            BY_OWNER.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(villagerId);
            return;
        }
        if (previous != null) {
            removeFromOwner(previous.owner(), villagerId);
        }
        BY_VILLAGER.put(villagerId, new Entry(owner, level.dimension()));
        BY_OWNER.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(villagerId);
    }

    private static Optional<Target> resolve(MinecraftServer server, UUID villagerId, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            remove(villagerId);
            return Optional.empty();
        }
        Entity entity = level.getEntity(villagerId);
        if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isBaby()) {
            remove(villagerId);
            return Optional.empty();
        }
        Optional<UUID> owner = HiredVillagerContractService.getHirer(level, villager);
        if (owner.isEmpty() || !owner.get().equals(entry.owner())) {
            remove(villagerId);
            return Optional.empty();
        }
        return Optional.of(new Target(level, villager));
    }

    private static void remove(UUID villagerId) {
        Entry previous = BY_VILLAGER.remove(villagerId);
        if (previous != null) {
            removeFromOwner(previous.owner(), villagerId);
        }
    }

    private static void removeIfIndexed(Villager villager) {
        if (villager == null) {
            return;
        }
        UUID villagerId = villager.getUUID();
        if (BY_VILLAGER.containsKey(villagerId)) {
            remove(villagerId);
        }
    }

    private static void removeFromOwner(UUID owner, UUID villagerId) {
        Set<UUID> owned = BY_OWNER.get(owner);
        if (owned == null) {
            return;
        }
        owned.remove(villagerId);
        if (owned.isEmpty()) {
            BY_OWNER.remove(owner);
        }
    }

    public record Target(ServerLevel level, Villager villager) {
    }

    private record Entry(UUID owner, ResourceKey<Level> dimension) {
    }
}
