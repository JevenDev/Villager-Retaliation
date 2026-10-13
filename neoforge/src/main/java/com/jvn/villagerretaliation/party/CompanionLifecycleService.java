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
        state.putBoolean("Recruited", true);
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
                    .ifPresent(home -> putHome(state, home.originDimension(), home.center()));
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
        putHome(state, player.serverLevel().dimension().location(), villager.blockPosition());
        state.putBoolean("HomeBed", false);
        state.putString("Reason", "Safe home agreed with owner");
        CompanionSavedData.get(player.serverLevel()).setDirty();
        return true;
    }

    public static boolean canPart(Villager villager) {
        if (VillagerDownedService.isDowned(villager) || villager.hurtTime > 0
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
            if (villager != null) {
                PartyQuickCommandService.onVillagerUnloaded(villager);
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
        state.putInt("DepartureTicks", Math.min(12000, elapsed + 1));
        if (villager.tickCount % 20 != 0) return true;
        data.setDirty();
        ResourceLocation homeDimension = ResourceLocation.tryParse(state.getString("HomeDimension"));
        ServerLevel homeLevel = homeDimension == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, homeDimension));
        BlockPos home = state.contains("HomePosition") ? BlockPos.of(state.getLong("HomePosition")) : null;
        if (homeLevel == null || home == null) {
            waitSafely(data, state, villager, "No registered home. Agree a safe home with /vr companion safehome");
            return true;
        }
        if (homeLevel != level && !state.getBoolean("AllowDimensionReturn")) {
            waitSafely(data, state, villager, "Home is in " + homeDimension + ". Owner must authorize dimension return");
            return true;
        }
        if (!homeLevel.hasChunkAt(home)) {
            waitSafely(data, state, villager, "Home chunk is unloaded. Return resumes when it is loaded");
            return true;
        }
        if (state.getBoolean("HomeBed") && !(homeLevel.getBlockState(home).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
            waitSafely(data, state, villager, "Registered home bed is missing. Agree a safe destination");
            return true;
        }
        BlockPos destination = safeDestination(homeLevel, villager, home);
        if (destination == null) {
            waitSafely(data, state, villager, "Home is unsafe. Waiting for a safe destination");
            return true;
        }
        if (homeLevel == level && villager.distanceToSqr(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D) <= 9.0D) {
            finish(data, state, level, villager);
            return true;
        }
        if (elapsed >= VillagerRetaliationConfig.COMPANION_DEPARTURE_MIN_TICKS.get()
                && unseen(level, villager.position(), villager) && unseen(homeLevel, net.minecraft.world.phys.Vec3.atCenterOf(destination), villager)) {
            if (homeLevel == level) {
                villager.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
                finish(data, state, level, villager);
            } else {
                EntityReturn.move(villager, homeLevel, destination);
                var moved = VillagerEntityResolver.loaded(level.getServer(), villager.getUUID());
                if (moved != null && moved.level() == homeLevel) finish(data, state, homeLevel, moved);
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
        status(data, state, "At home", "Returned safely and resumed village life");
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
            if (player.isSpectator()) continue;
            double distance = player.distanceToSqr(position);
            if (distance <= 1024.0D || distance <= 4096.0D && (villager.level() != level || player.hasLineOfSight(villager))) return false;
        }
        return true;
    }

    static BlockPos safeDestination(ServerLevel level, Villager villager, BlockPos home) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dy = -2; dy <= 2; dy++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                BlockPos pos = home.offset(dx, dy, dz);
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                var floor = level.getBlockState(pos.below());
                if (!floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                        || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.CAMPFIRE)
                        || floor.is(Blocks.SOUL_CAMPFIRE) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                        || !level.getFluidState(pos).isEmpty()) continue;
                double half = villager.getBbWidth() / 2.0D;
                AABB bounds = new AABB(pos.getX() + 0.5D - half, pos.getY(), pos.getZ() + 0.5D - half,
                        pos.getX() + 0.5D + half, pos.getY() + villager.getBbHeight(), pos.getZ() + 0.5D + half);
                if (level.noCollision(villager, bounds) && !hasThreat(level, villager, new AABB(pos).inflate(16.0D))) return pos;
            }
        }
        return null;
    }

    private static boolean hasThreat(ServerLevel level, Villager villager, AABB area) {
        return !level.getEntitiesOfClass(LivingEntity.class, area, candidate -> candidate.isAlive() && candidate != villager
                && VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, candidate)).isEmpty();
    }

    private static final class EntityReturn {
        static void move(Villager villager, ServerLevel target, BlockPos destination) {
            villager.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(target,
                    net.minecraft.world.phys.Vec3.atBottomCenterOf(destination), net.minecraft.world.phys.Vec3.ZERO,
                    villager.getYRot(), villager.getXRot(), net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        }
    }
}
