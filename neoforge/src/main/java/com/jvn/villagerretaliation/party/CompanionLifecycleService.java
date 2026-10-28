package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceEntityData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCommandController;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class CompanionLifecycleService {
    private CompanionLifecycleService() {
    }

    public static UUID owner(ServerLevel level, UUID villagerId) {
        var state = CompanionSavedData.get(level).find(villagerId);
        return state != null && state.hasUUID("Owner") ? state.getUUID("Owner") : null;
    }

    public static void associate(ServerLevel level, Villager villager, PartyVillagerRecord record) {
        var data = CompanionSavedData.get(level);
        var state = data.getOrCreate(villager.getUUID());
        state.putUUID("Owner", record.recruiterId());
        state.putUUID("Commander", record.commanderId());
        if (!state.contains("ActiveService")) state.putBoolean("Recruited", true);
        CompanionGrowthService.rememberProfession(villager, state);
        captureHome(level, villager, state);
        rememberLocation(level, villager, state);
        data.setDirty();
    }

    public static void migrate(MinecraftServer server) {
        for (PartyRecord party : PartySavedData.get(server.overworld()).partyRecords()) {
            for (PartyVillagerRecord record : party.villagers()) {
                var data = CompanionSavedData.get(server.overworld());
                var state = data.getOrCreate(record.villagerId());
                if (!state.hasUUID("Owner")) {
                    state.putUUID("Owner", record.recruiterId());
                    state.putBoolean("Recruited", true);
                    state.putString("Status", "Active");
                    data.setDirty();
                }
                Villager loaded = VillagerEntityResolver.loaded(server, record.villagerId());
                if (loaded != null && loaded.level() instanceof ServerLevel level) {
                    captureHome(level, loaded, state);
                    if (!state.hasUUID("Commander") || !state.getUUID("Commander").equals(record.commanderId())) {
                        state.putUUID("Commander", record.commanderId());
                        data.setDirty();
                    }
                }
            }
        }
    }

    private static void captureHome(ServerLevel level, Villager villager, CompoundTag state) {
        if (state.contains("HomeDimension")) return;
        var bed = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (bed != null) {
            putHome(state, bed.dimension().location(), bed.pos());
            state.putBoolean("HomeBed", true);
        } else {
            VillageAllegianceEntityData.read(villager).flatMap(allegiance ->
                    VillageAllegianceRegistrySavedData.get(level).canonicalRecord(allegiance.primary()))
                    .filter(home -> !home.archived() && home.originDimension() != null)
                    .ifPresent(home -> {
                        putHome(state, home.originDimension(), home.center());
                        state.putUUID("HomeVillage", home.id().value());
                    });
        }
        if (state.contains("HomeDimension")) CompanionSavedData.get(level).setDirty();
    }

    private static void putHome(CompoundTag state, ResourceLocation dimension, BlockPos position) {
        state.putString("HomeDimension", dimension.toString());
        state.putLong("HomePosition", position.asLong());
    }

    public static boolean setSafeHome(ServerPlayer player, Villager villager) {
        if (player.serverLevel() != villager.level() || player.distanceToSqr(villager) > 64.0D
                || !player.getUUID().equals(owner(player.serverLevel(), villager.getUUID()))
                || !canPart(villager) || safeDestination(player.serverLevel(), villager, villager.blockPosition()) == null) return false;
        var state = CompanionSavedData.get(player.serverLevel()).find(villager.getUUID());
        state.putString("FallbackDimension", player.serverLevel().dimension().location().toString());
        state.putLong("FallbackPosition", safeDestination(player.serverLevel(), villager, villager.blockPosition()).asLong());
        state.putString("Reason", "Safe fallback agreed with owner; registered home preserved");
        CompanionSavedData.get(player.serverLevel()).setDirty();
        return true;
    }

    public static boolean canPart(Villager villager) {
        if (!villager.isAlive() || villager.isRemoved() || VillagerDownedService.isDowned(villager) || villager.hurtTime > 0
                || VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)
                || villager.getTarget() != null && villager.getTarget().isAlive()) return false;
        if (!(villager.level() instanceof ServerLevel level)) return false;
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        return state == null || !state.contains("LastCombat")
                || level.getServer().overworld().getGameTime() - state.getLong("LastCombat") >= 200L;
    }

    public static void damaged(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var data = CompanionSavedData.get(level);
        var state = data.find(villager.getUUID());
        if (state != null) {
            state.putLong("LastCombat", level.getServer().overworld().getGameTime());
            data.setDirty();
        }
    }

    public static void beginDeparture(ServerLevel level, UUID villagerId, String reason) {
        var data = CompanionSavedData.get(level);
        var state = data.getOrCreate(villagerId);
        state.putBoolean("Returning", true);
        state.putString("Status", "Returning home");
        state.putString("Reason", reason);
        state.putInt("DepartureTicks", 0);
        state.remove("WalkTarget");
        data.setDirty();
        var villager = VillagerEntityResolver.loaded(level.getServer(), villagerId);
        if (villager != null) {
            PartyQuickCommandService.onVillagerUnloaded(villager);
            VillagerCommandController.clear(villager);
        }
    }

    public static void rememberLocation(ServerLevel level, Villager villager, CompoundTag state) {
        state.putString("LastDimension", level.dimension().location().toString());
        state.putLong("LastPosition", villager.blockPosition().asLong());
        state.putString("Name", villager.getDisplayName().getString());
        CompanionSavedData.get(level).setDirty();
    }

    public static void onLogout(ServerPlayer player) {
        PartyQuickCommandService.onPlayerLogout(player);
        var party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        if (party == null) return;
        for (var record : party.villagers()) {
            if (!record.commanderId().equals(player.getUUID())) continue;
            var data = CompanionSavedData.get(player.serverLevel());
            var state = data.getOrCreate(record.villagerId());
            state.putString("Status", "Waiting for commander");
            state.putString("Reason", "Commander logged out");
            data.setDirty();
            var villager = VillagerEntityResolver.loaded(player.getServer(), record.villagerId());
            if (villager != null && !PartyQuickCommandService.overridesRecruitmentMovement(villager)) {
                villager.getNavigation().stop();
            }
        }
    }

    public static void resume(ServerLevel level, UUID villagerId, boolean following) {
        var data = CompanionSavedData.get(level);
        var state = data.find(villagerId);
        if (state == null) return;
        state.putBoolean("Returning", false);
        state.putString("Status", following ? "Active" : "Waiting here");
        state.putString("Reason", following ? "Following assigned commander" : "Wait Here keeps the active party slot");
        state.remove("DeathWaitStart");
        data.setDirty();
    }

    public static boolean tick(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        var data = CompanionSavedData.get(level);
        var state = data.find(villager.getUUID());
        if (state == null) return false;
        if (villager.tickCount % 20 == 0) {
            rememberLocation(level, villager, state);
            captureHome(level, villager, state);
        }
        var party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        var record = party == null ? null : party.villager(villager.getUUID());
        if (!state.getBoolean("Returning")) {
            if (record == null || state.getString("Status").equals("At home")) return false;
            if (state.contains("ActiveService") && level.getServer().overworld().getGameTime() >= state.getCompound("ActiveService").getLong("End")) {
                status(data, state, "Finishing service", "Service ended; waiting for a safe goodbye (still occupies a slot)");
                return false;
            }
            var commander = level.getServer().getPlayerList().getPlayer(record.commanderId());
            if (record.commandMode() == PartyCommandMode.STAY) {
                status(data, state, "Waiting here", "Wait Here keeps the active party slot");
            } else if (commander == null) {
                status(data, state, "Waiting for commander", "Commander offline");
            } else if (!commander.isAlive() || commander.isSpectator()) {
                status(data, state, "Waiting after player death", "Defending this position before returning home");
                if (!state.contains("DeathWaitStart")) {
                    state.putLong("DeathWaitStart", level.getServer().overworld().getGameTime());
                    data.setDirty();
                }
                if (level.getServer().overworld().getGameTime() - state.getLong("DeathWaitStart")
                        >= VillagerRetaliationConfig.COMPANION_DEATH_WAIT_TICKS.get() && canPart(villager)) {
                    beginDeparture(level, villager.getUUID(), "Commander did not return after death");
                }
            } else if (commander.serverLevel() != level) {
                status(data, state, "Waiting for dimension travel", "Commander is in another dimension");
            } else {
                state.remove("DeathWaitStart");
                status(data, state, "Active", "Following assigned commander");
            }
            if (!state.getBoolean("Returning")) return false;
        }
        if (!canPart(villager) || hasThreat(level, villager, villager.getBoundingBox().inflate(16.0D))) {
            status(data, state, "Returning home", "Waiting for combat to end");
            return false;
        }
        if (villager.getPersistentData().hasUUID("VillagerRetaliationPartyId")) {
            com.jvn.villagerretaliation.interaction.VillagerRecruitmentService.clearPartyFollowing(villager);
        }
        int elapsed = state.getInt("DepartureTicks");
        state.putInt("DepartureTicks", elapsed == Integer.MAX_VALUE ? elapsed : elapsed + 1);
        if (villager.tickCount % 20 != 0) return true;
        data.setDirty();
        HomeTarget target = resolveHome(level, villager, state, false);
        if (target.destination() == null && state.contains("FallbackDimension")) {
            HomeTarget fallback = resolveHome(level, villager, state, true);
            if (fallback.destination() != null) target = fallback;
            else target = new HomeTarget(null, null, target.reason() + "; fallback: " + fallback.reason());
        }
        if (target.destination() == null) {
            waitSafely(data, state, villager, target.reason());
            return true;
        }
        ServerLevel homeLevel = target.level();
        BlockPos destination = target.destination();
        BlockPos home = destination;
        state.putString("ReturnDestination", target.reason());
        if (homeLevel == level && villager.distanceToSqr(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D) <= 9.0D) {
            finish(data, state, level, villager);
            return true;
        }
        if (elapsed >= VillagerRetaliationConfig.COMPANION_DEPARTURE_MIN_TICKS.get()
                && unseen(level, villager.position(), villager) && unseen(homeLevel, net.minecraft.world.phys.Vec3.atBottomCenterOf(destination), villager)) {
            if (homeLevel == level) {
                villager.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
                finish(data, state, level, villager);
            } else {
                var moved = EntityReturn.move(villager, homeLevel, destination);
                if (moved instanceof Villager returned && returned.level() == homeLevel) finish(data, state, homeLevel, returned);
                else waitSafely(data, state, villager, "Dimension transition unavailable; waiting for safe return");
            }
            return true;
        }
        if (elapsed >= VillagerRetaliationConfig.COMPANION_DEPARTURE_TIMEOUT_TICKS.get()) {
            waitSafely(data, state, villager, "Path blocked or watched. Waiting out of combat until return is safe");
            return true;
        }
        BlockPos walkTarget;
        if (homeLevel == level && villager.blockPosition().distSqr(home) <= 4096.0D) {
            walkTarget = destination;
        } else if (state.contains("WalkTarget")) {
            walkTarget = BlockPos.of(state.getLong("WalkTarget"));
        } else {
            var nearest = level.getNearestPlayer(villager, 64.0D);
            double dx = nearest == null ? 1.0D : villager.getX() - nearest.getX();
            double dz = nearest == null ? 0.0D : villager.getZ() - nearest.getZ();
            double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
            walkTarget = safeDestination(level, villager, villager.blockPosition().offset((int)(16 * dx / length), 0, (int)(16 * dz / length)));
            if (walkTarget != null) state.putLong("WalkTarget", walkTarget.asLong());
        }
        status(data, state, "Returning home", "Walking away before safe return");
        if (walkTarget != null) {
            com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
            villager.getNavigation().moveTo(walkTarget.getX() + 0.5D, walkTarget.getY(), walkTarget.getZ() + 0.5D, 0.5D);
        }
        return true;
    }

    private static void finish(CompanionSavedData data, CompoundTag state, ServerLevel level, Villager villager) {
        state.putBoolean("Returning", false);
        state.remove("DeathWaitStart");
        state.remove("WalkTarget");
        status(data, state, "At home", "Returned safely to " + state.getString("ReturnDestination") + "; resumed village life");
        if (state.hasUUID("Owner")) CompanionRelationshipService.experience(level, villager.getUUID(), state.getUUID("Owner"),
                "villagerretaliation:returned_home", 2);
        VillagerCommandController.clear(villager);
        VillagerBehaviorSuppressionPolicy.restoreAfterRelease(level, villager);
        rememberLocation(level, villager, state);
    }

    private static void waitSafely(CompanionSavedData data, CompoundTag state, Villager villager, String reason) {
        villager.getNavigation().stop();
        status(data, state, "Safe waiting", reason);
    }

    private static void status(CompanionSavedData data, CompoundTag state, String status, String reason) {
        if (status.equals(state.getString("Status")) && reason.equals(state.getString("Reason"))) return;
        state.putString("Status", status);
        state.putString("Reason", reason);
        data.setDirty();
    }

    static boolean unseen(ServerLevel level, net.minecraft.world.phys.Vec3 position, Villager villager) {
        for (var player : level.players()) {
            double distance = player.distanceToSqr(position);
            if (distance <= 1024.0D) return false;
            if (distance <= 4096.0D) {
                // Ray trace to the candidate position, including in the destination dimension.
                // Testing line of sight to the source entity could visibly pop it into someone's home.
                for (double height : new double[]{0.4D, villager.getEyeHeight()}) {
                    var hit = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), position.add(0, height, 0),
                            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return false;
                }
            }
        }
        return true;
    }

    static BlockPos safeDestination(ServerLevel level, Villager villager, BlockPos home) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dy = -2; dy <= 2; dy++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                BlockPos pos = home.offset(dx, dy, dz);
                if (!level.hasChunkAt(pos) || pos.getY() <= level.getMinBuildHeight() || pos.getY() + villager.getBbHeight() >= level.getMaxBuildHeight()
                        || !level.getWorldBorder().isWithinBounds(pos)) continue;
                var floor = level.getBlockState(pos.below());
                if (!floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                        || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.CAMPFIRE)
                        || floor.is(Blocks.SOUL_CAMPFIRE) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                        || !level.getFluidState(pos).isEmpty() || hazardousNeighbourhood(level, pos)) continue;
                double half = villager.getBbWidth() / 2.0D;
                AABB bounds = new AABB(pos.getX() + 0.5D - half, pos.getY(), pos.getZ() + 0.5D - half,
                        pos.getX() + 0.5D + half, pos.getY() + villager.getBbHeight(), pos.getZ() + 0.5D + half);
                if (level.getWorldBorder().isWithinBounds(bounds) && level.noCollision(villager, bounds)
                        && !hasThreat(level, villager, new AABB(pos).inflate(16.0D))) return pos;
            }
        }
        return null;
    }

    private static boolean hasThreat(ServerLevel level, Villager villager, AABB area) {
        return !level.getEntitiesOfClass(LivingEntity.class, area, candidate -> candidate.isAlive() && candidate != villager
                && VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, candidate)).isEmpty();
    }

    private static final class EntityReturn {
        static net.minecraft.world.entity.Entity move(Villager villager, ServerLevel target, BlockPos destination) {
            return villager.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(target,
                    net.minecraft.world.phys.Vec3.atBottomCenterOf(destination), net.minecraft.world.phys.Vec3.ZERO,
                    villager.getYRot(), villager.getXRot(), net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        }
    }

    private static HomeTarget resolveHome(ServerLevel source, Villager villager, CompoundTag state, boolean fallback) {
        if (!fallback && state.hasUUID("HomeVillage")) {
            var village = VillageAllegianceRegistrySavedData.get(source).canonicalRecord(
                    new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(state.getUUID("HomeVillage"))).orElse(null);
            if (village == null || village.archived()) return new HomeTarget(null, null, "Registered village no longer exists. Agree a safe fallback");
        }
        String prefix = fallback ? "Fallback" : "Home";
        var dimension = ResourceLocation.tryParse(state.getString(prefix + "Dimension"));
        var level = dimension == null ? null : source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !state.contains(prefix + "Position"))
            return new HomeTarget(null, null, "No registered home. Agree a safe fallback with /vr companion safehome");
        if (level != source && !state.getBoolean("AllowDimensionReturn"))
            return new HomeTarget(null, null, "Home is in " + dimension + ". Owner must authorize dimension return");
        var pos = BlockPos.of(state.getLong(prefix + "Position"));
        if (!level.hasChunkAt(pos)) return new HomeTarget(null, null, "Home chunk is unloaded. Return resumes when it is loaded");
        if (level != source && !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong()))
            return new HomeTarget(null, null, "Home entity storage is not loaded. Waiting for a safe dimension transition");
        if (!fallback && state.getBoolean("HomeBed") && !(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock))
            return new HomeTarget(null, null, "Registered home bed is missing. Agree a safe fallback");
        var safe = safeDestination(level, villager, pos);
        return new HomeTarget(level, safe, safe == null ? "Home is unsafe. Waiting for a safe destination"
                : (fallback ? "agreed fallback" : "registered home") + " in " + dimension + " " + safe.toShortString());
    }

    private static boolean hazardousNeighbourhood(ServerLevel level, BlockPos pos) {
        var hazards = net.minecraft.tags.TagKey.create(Registries.BLOCK, ResourceLocation.parse("villagerretaliation:unsafe_companion_return"));
        for (var neighbour : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
            if (!level.hasChunkAt(neighbour) || level.getBlockState(neighbour).is(hazards) || !level.getFluidState(neighbour).isEmpty()) return true;
        }
        return false;
    }

    private record HomeTarget(ServerLevel level, BlockPos destination, String reason) { }
}
