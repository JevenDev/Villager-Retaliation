package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

final class SettlementArrivalService {
    private SettlementArrivalService() {
    }

    static void admit(ServerLevel level, SettlementRecord town, Villager resident) {
        if (town.arrivals().containsKey(resident.getUUID())) {
            throw new IllegalArgumentException("Arrival duplicates a resident identity");
        }
        VillageAllegianceApi.assignKnown(level, resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        VillagerSocialGraphSavedData.get(level).ensureProfile(level, resident);
        town.recordResident(SettlementResidentState.capture(resident, town, level.getGameTime()));
        town.recordArrival(new SettlementArrival(resident.getUUID(), resident.saveWithoutId(new CompoundTag())));
        town.recordEvent(level.getGameTime(), SettlementEvent.Type.RESIDENT_JOINED,
                com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry.resolveDisplayName(resident).getString());
    }

    static void update(ServerLevel level, SettlementRecord town) {
        for (UUID id : List.copyOf(town.arrivals().keySet())) {
            materialize(level, town, id);
        }
    }

    static boolean materialize(ServerLevel level, SettlementRecord town, UUID id) {
        var arrival = town.arrivals().get(id);
        if (arrival == null || !town.dimension().equals(level.dimension().location())) return false;
        var center = new ChunkPos(arrival.origin(town));
        var chunk = level.getChunkSource().getChunkNow(center.x, center.z);
        if (chunk == null) return false;
        String key = "Arrival:" + town.id().value() + ":" + id;
        if (SettlementChunkReceipts.version(chunk, key, id) >= 1L) {
            town.removeArrival(id);
            return true;
        }
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            if (dimension.getEntity(id) != null) {
                SettlementChunkReceipts.applied(chunk, key, id, 1L);
                town.removeArrival(id);
                return true;
            }
        }
        var care = town.residents().get(id);
        var departure = town.departures().get(id);
        boolean member = VillageAllegianceRegistrySavedData.get(level).record(town.id())
                .map(record -> record.residents().containsKey(id)).orElse(false);
        if (departure == null && (!member || care == null)) {
            town.removeArrival(id);
            return false;
        }
        if (!level.areEntitiesLoaded(center.toLong())) return false;
        Villager resident = EntityType.VILLAGER.create(level);
        resident.load(arrival.entity());
        if (departure != null) {
            departure.care().restore(resident);
        } else {
            care.restore(resident);
        }
        var position = arrivalPosition(level, arrival.origin(town), resident);
        if (position.isEmpty()) return false;
        resident.setPos(Vec3.atBottomCenterOf(position.get()));
        if (!level.addFreshEntity(resident)) return false;
        SettlementChunkReceipts.applied(chunk, key, id, 1L);
        town.removeArrival(id);
        SettlementHousingService.reconcile(resident);
        if (care != null) care.remember(resident);
        return true;
    }

    private static Optional<BlockPos> arrivalPosition(ServerLevel level, BlockPos origin, Villager resident) {
        for (BlockPos offset : BlockPos.betweenClosed(-4, -2, -4, 4, 3, 4)) {
            BlockPos pos = origin.offset(offset);
            if (!level.hasChunkAt(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos))
                    || level.isOutsideBuildHeight(pos.below()) || level.isOutsideBuildHeight(pos.above())
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                    || !level.getFluidState(pos).isEmpty()) {
                continue;
            }
            var box = resident.getDimensions(resident.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            if (level.hasChunkAt(min) && level.hasChunkAt(max)
                    && level.areEntitiesLoaded(ChunkPos.asLong(min)) && level.areEntitiesLoaded(ChunkPos.asLong(max))
                    && level.getFluidState(pos.above()).isEmpty() && level.noCollision(resident, box)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }
}
