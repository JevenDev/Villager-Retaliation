package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.network.HiredFishingRequestPayload;
import com.jvn.villagerretaliation.network.HiredFishingSnapshotPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HiredFishingSpotService {
    private static final String REVISION = "FishingSpotsRevision";

    private HiredFishingSpotService() {
    }

    public static void handle(ServerPlayer player, HiredFishingRequestPayload request) {
        var key = request.spots() == null ? com.jvn.villagerretaliation.VillagerRetaliation.id("hired_fishing_view")
                : HiredFishingRequestPayload.TYPE.id();
        boolean allowed = ServerboundRequestLimiter.tryAcquire(player, key, 3L);
        String feedback = allowed ? request.spots() == null ? "" : apply(player, request) : "Please wait before editing fishing spots again";
        PacketDistributor.sendToPlayer(player, snapshot(player, request, allowed && request.spots() != null && feedback.isEmpty(), feedback));
    }

    public static String apply(ServerPlayer player, HiredFishingRequestPayload request) {
        var villager = owned(player, request);
        if (villager == null) return "Personal fisher is unavailable";
        if (request.spots() == null) return "";
        var state = HiredWorkStateStore.state(villager);
        long revision = Math.max(0L, state.getLong(REVISION));
        if (revision != request.revision()) return "Fishing settings changed. Refresh before saving";
        if (revision == Long.MAX_VALUE) return "Fishing settings require recovery";
        HiredFishingSpots previous;
        try {
            previous = HiredFishingSpots.read(state);
        } catch (IllegalArgumentException invalid) {
            previous = HiredFishingSpots.AUTOMATIC;
        }
        var bounds = HiredWorkArea.fromState(state, villager.blockPosition());
        if (!valid(player.serverLevel(), bounds, previous, request.spots(), ignored -> false)) {
            return "Choose open water near a dry shore inside the assigned work area";
        }
        request.spots().apply(state);
        state.putLong(REVISION, revision + 1L);
        state.remove("FishingPreferredScanCursor");
        state.remove("NextFishingSpotScanGameTime");
        HiredWorkerBrain.clearFailure(HiredWorkSession.active(player.serverLevel(), villager).context());
        HiredWorkSession.invalidate(villager);
        return "";
    }

    public static HiredFishingSnapshotPayload snapshot(ServerPlayer player, HiredFishingRequestPayload request,
                                                       boolean applied, String feedback) {
        var villager = owned(player, request);
        CompoundTag state = villager == null ? new CompoundTag() : HiredWorkStateStore.state(villager);
        HiredFishingSpots spots;
        try {
            spots = HiredFishingSpots.read(state);
        } catch (IllegalArgumentException invalid) {
            spots = HiredFishingSpots.AUTOMATIC;
            if (feedback.isEmpty()) feedback = "Saved spots are damaged. Save a replacement list or choose Automatic to reset them";
        }
        if (villager == null) feedback = "Personal fisher is unavailable";
        return new HiredFishingSnapshotPayload(player.serverLevel().dimension().location(), request.worker(), request.request(),
                Math.max(0L, state.getLong(REVISION)), spots, villager != null, applied, feedback);
    }

    private static Villager owned(ServerPlayer player, HiredFishingRequestPayload request) {
        if (player == null || !(player.serverLevel().getEntity(request.worker()) instanceof Villager villager)
                || !HiredProductionService.owns(player, villager)
                || VillagerWorkAuthority.resolve(player.serverLevel(), villager).role() != HiredVillagerRole.FISHING) return null;
        return villager;
    }

    public static boolean valid(ServerLevel level, HiredWorkArea bounds, HiredFishingSpots previous,
                                HiredFishingSpots requested, Predicate<BlockPos> observed) {
        if (requested.positions().isEmpty()) return true;
        if (!bounds.usable() || !requested.within(bounds)) return false;
        for (BlockPos water : requested.positions()) {
            if (level.isOutsideBuildHeight(water) || !level.getWorldBorder().isWithinBounds(water)) return false;
            if (previous.positions().contains(water) || observed.test(water)) continue;
            if (!level.hasChunkAt(water) || !level.getFluidState(water).is(FluidTags.WATER)
                    || level.getFluidState(water.above()).is(FluidTags.WATER)
                    || !level.getBlockState(water).getCollisionShape(level, water).isEmpty()) return false;
            boolean shore = false;
            for (BlockPos approach : BlockPos.betweenClosed(water.offset(-6, -2, -6), water.offset(6, 2, 6))) {
                if (approach.getX() < bounds.min().getX() || approach.getX() > bounds.max().getX()
                        || approach.getY() < bounds.min().getY() || approach.getY() > bounds.max().getY()
                        || approach.getZ() < bounds.min().getZ() || approach.getZ() > bounds.max().getZ()
                        || approach.distSqr(water) > 64.0D) continue;
                if (HiredMoveToBlockFaceJob.isValidApproachPosition(level, approach)
                        && level.getFluidState(approach).isEmpty() && level.getFluidState(approach.above()).isEmpty()) {
                    shore = true;
                    break;
                }
            }
            if (!shore) return false;
        }
        return true;
    }
}
