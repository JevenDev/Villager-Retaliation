package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;

public final class SettlementProjectileService {
    private static final ThreadLocal<Projectile> RESTORING = new ThreadLocal<>();

    private SettlementProjectileService() {
    }

    static void launched(SettlementRecord town, SettlementWorkerState worker, Projectile projectile, long now) {
        if (!town.dimension().equals(projectile.level().dimension().location())) {
            throw new IllegalArgumentException("Settlement projectile belongs to another dimension");
        }
        if (!town.projectiles().containsKey(projectile.getUUID())) {
            town.recordProjectile(SettlementProjectile.launched(projectile, worker, now));
        }
    }

    public static void onAdded(Projectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level) || SettlementProjectileCapture.capturing(projectile)) return;
        if (owner(level, projectile) != null) {
            onChanged(projectile);
            return;
        }
        if (!(projectile.getOwner() instanceof Villager owner)) return;
        var town = SettlementWorkforceService.home(level, owner).orElse(null);
        if (town == null || !town.dimension().equals(level.dimension().location())) return;
        var worker = town.workers().get(owner.getUUID());
        if (worker == null || worker.role() != com.jvn.villagerretaliation.interaction.HiredVillagerRole.HUNTING) return;
        launched(town, worker, projectile, level.getGameTime());
        onChanged(projectile);
    }

    public static void onChanged(Projectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level) || RESTORING.get() == projectile
                || SettlementProjectileCapture.capturing(projectile)) return;
        var town = owner(level, projectile);
        if (town == null) return;
        var state = synchronize(level, town, town.projectiles().get(projectile.getUUID()));
        if (state.removed() && SettlementProjectile.version(projectile) <= state.version()) {
            restore(state, projectile);
            return;
        }
        state.observe(projectile, level.getGameTime());
        receipts(level, state);
        town.changed();
    }

    public static boolean onActive(Projectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level) || RESTORING.get() == projectile) return !projectile.isRemoved();
        var town = owner(level, projectile);
        var state = town == null ? null : town.projectiles().get(projectile.getUUID());
        var existing = state == null ? null : existing(level, state);
        if (existing != null && existing != projectile || state != null && existing == null && known(level, state)) return false;
        var chunk = level.getChunkSource().getChunkNow(projectile.blockPosition().getX() >> 4, projectile.blockPosition().getZ() >> 4);
        if (chunk != null) {
            var tag = SettlementChunkReceipts.projectiles(chunk, false).getCompound(projectile.getUUID().toString());
            if (!tag.isEmpty()) restore(SettlementProjectile.load(tag), projectile);
        }
        if (town != null) {
            state = synchronize(level, town, state);
            if (SettlementProjectile.version(projectile) > state.version()) state.observe(projectile, level.getGameTime());
            state.anchor(projectile.blockPosition());
            if (existing == null && !state.removed()) state.prepareSpawn();
            receipts(level, state);
            restore(state, projectile);
            receipts(level, state);
            town.changed();
        }
        return !projectile.isRemoved();
    }

    public static void onUnloaded(Projectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level) || RESTORING.get() == projectile
                || SettlementProjectileCapture.capturing(projectile)) return;
        var town = owner(level, projectile);
        if (town == null) return;
        var state = synchronize(level, town, town.projectiles().get(projectile.getUUID()));
        var reason = projectile.getRemovalReason();
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            town.removeProjectile(state.id());
        } else if (reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            state.anchor(projectile.blockPosition());
            state.remove();
            remove(level, town, state);
        } else if (!state.removed()) {
            restore(state, projectile);
            state.observe(projectile, level.getGameTime());
            receipts(level, state);
            town.changed();
        }
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var original : List.copyOf(town.projectiles().values())) {
            var state = synchronize(level, town, original);
            var actual = existing(level, state);
            if (!prepareBackground(level, town, state, actual, now)) continue;
            var owner = state.shooter(level, town);
            if (owner != null) advance(level, town, state, owner, null, now);
            if (actual instanceof Projectile projectile) restore(state, projectile);
            materialize(level, town, state);
        }
    }

    static void advanceOwned(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, Villager owner,
                             net.minecraft.world.entity.LivingEntity target, long now) {
        for (var original : List.copyOf(town.projectiles().values())) {
            if (!original.owner().equals(worker.id())) continue;
            var state = synchronize(level, town, original);
            var actual = existing(level, state);
            if (!prepareBackground(level, town, state, actual, now)) continue;
            advance(level, town, state, owner, target, now);
            if (actual instanceof Projectile projectile && projectile.level() == level) restore(state, projectile);
            materialize(level, town, state);
        }
    }

    private static boolean prepareBackground(ServerLevel level, SettlementRecord town, SettlementProjectile state, Entity actual, long now) {
        if (actual instanceof Projectile projectile && projectile.level() == level && SettlementProjectile.version(projectile) > state.version()) {
            state.observe(projectile, Math.max(now, state.checkedAt()));
            receipts(level, state);
            town.changed();
        }
        if (state.removed()) {
            if (actual instanceof Projectile projectile) restore(state, projectile);
            remove(level, town, state);
            return false;
        }
        if (actual instanceof Projectile projectile && projectile.level() == level) {
            restore(state, projectile);
            if (level.isPositionEntityTicking(projectile.blockPosition())) {
                if (now > state.checkedAt()) {
                    state.observe(projectile, now);
                    receipts(level, state);
                    town.changed();
                }
                return false;
            }
            return true;
        }
        if (actual == null && known(level, state)) return false;
        if (actual != null || !state.pendingSpawn() && level.areEntitiesLoaded(ChunkPos.asLong(state.nativePosition()))) {
            state.remove();
            remove(level, town, state);
            return false;
        }
        return true;
    }

    private static void advance(ServerLevel level, SettlementRecord town, SettlementProjectile state, Villager owner,
                                net.minecraft.world.entity.LivingEntity target, long now) {
        if (state.removed() || now <= state.checkedAt()) return;
        var projectile = state.context(level);
        if (projectile == null) {
            state.checked(now);
            return;
        }
        projectile.setOwner(owner);
        var simulation = new SettlementProjectileSimulation(level, town, state, owner, target);
        long start = Math.max(state.checkedAt() + 1L, Math.max(0L, now - 199L));
        try {
            for (long at = start; at <= now && !projectile.isRemoved(); at++) {
                if (level.isPositionEntityTicking(projectile.blockPosition())) break;
                if (!simulation.step(projectile, at)) {
                    var worker = town.workers().get(state.owner());
                    if (worker != null) worker.status("hunting_projectile_needs_world");
                    break;
                }
                state.advance(projectile, at);
            }
        } catch (SettlementExperienceTerrain.Unobserved unavailable) {
            var worker = town.workers().get(state.owner());
            if (worker != null) worker.status("hunting_visibility_unobserved");
        }
        state.checked(now);
        receipts(level, state);
        if (state.removed()) remove(level, town, state);
        town.changed();
    }

    private static void materialize(ServerLevel level, SettlementRecord town, SettlementProjectile state) {
        if (state.removed()) {
            remove(level, town, state);
            return;
        }
        if (known(level, state)) return;
        var pos = state.position();
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.areEntitiesLoaded(ChunkPos.asLong(pos))
                || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
        var projectile = state.context(level);
        if (projectile == null) return;
        state.prepareSpawn();
        receipts(level, state);
        if (level.addFreshEntity(projectile)) {
            state.observe(projectile, level.getGameTime());
            receipts(level, state);
            town.changed();
        }
    }

    private static Entity existing(ServerLevel level, SettlementProjectile state) {
        var visible = SettlementAnimalBirthService.existing(level, state.id());
        if (visible != null) return visible;
        for (var dimension : level.getServer().getAllLevels()) {
            var manager = ((com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor) dimension).villagerretaliation$entityManager();
            if (!manager.isLoaded(state.id())) continue;
            var sections = ((com.jvn.villagerretaliation.mixin.PersistentEntitySectionManagerAccessor) manager).villagerretaliation$sectionStorage();
            for (long anchor : state.nativeChunks()) {
                var entity = sections.getExistingSectionsInChunk(anchor).flatMap(section -> section.getEntities())
                        .filter(candidate -> candidate.getUUID().equals(state.id())).findFirst().orElse(null);
                if (entity != null) return entity;
            }
        }
        return null;
    }

    private static boolean known(ServerLevel level, SettlementProjectile state) {
        for (var dimension : level.getServer().getAllLevels()) {
            if (((com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor) dimension)
                    .villagerretaliation$entityManager().isLoaded(state.id())) return true;
        }
        return false;
    }

    private static SettlementRecord owner(ServerLevel level, Projectile projectile) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.dimension().equals(level.dimension().location()) && town.projectiles().containsKey(projectile.getUUID())) return town;
        }
        return null;
    }

    private static void restore(SettlementProjectile state, Projectile projectile) {
        var previous = RESTORING.get();
        RESTORING.set(projectile);
        try {
            state.restore(projectile);
        } finally {
            if (previous == null) RESTORING.remove();
            else RESTORING.set(previous);
        }
    }

    private static SettlementProjectile synchronize(ServerLevel level, SettlementRecord town, SettlementProjectile state) {
        var latest = state;
        for (long anchor : state.nativeChunks()) {
            var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(anchor), ChunkPos.getZ(anchor));
            if (chunk == null) continue;
            var tag = SettlementChunkReceipts.projectiles(chunk, false).getCompound(state.id().toString());
            if (tag.isEmpty() || tag.getLong("Version") < latest.version()
                    || tag.getLong("Version") == latest.version() && !tag.getBoolean("Removed")) continue;
            var receipt = SettlementProjectile.load(tag);
            if (!receipt.id().equals(state.id())) throw new IllegalStateException("Projectile receipt identity differs");
            receipt.retainAnchors(latest);
            latest = receipt;
        }
        if (latest != state) {
            town.removeProjectile(state.id());
            town.recordProjectile(latest);
        }
        return latest;
    }

    private static void receipts(ServerLevel level, SettlementProjectile state) {
        for (long anchor : state.nativeChunks()) {
            var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(anchor), ChunkPos.getZ(anchor));
            if (chunk == null) continue;
            var entries = SettlementChunkReceipts.projectiles(chunk, true);
            String key = state.id().toString();
            if (entries.getCompound(key).getLong("Version") > state.version()) continue;
            state.acknowledge(anchor);
            entries.put(key, state.save());
            chunk.setUnsaved(true);
        }
    }

    private static void remove(ServerLevel level, SettlementRecord town, SettlementProjectile state) {
        receipts(level, state);
        if (state.acknowledged()) town.removeProjectile(state.id());
        town.changed();
    }
}
