package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementFoundingService {
    private SettlementFoundingService() {
    }

    public static Result found(ServerPlayer player, BlockPos center) {
        var level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(center.getCenter()) > 64.0D
                || !level.hasChunkAt(center)
                || !level.getBlockState(center).is(VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get())) {
            return new Result(Status.UNAVAILABLE, Optional.empty());
        }
        var data = SettlementSavedData.get(level);
        var existing = data.at(level.dimension().location(), center);
        if (existing.isPresent()) {
            return existing.get().permits(player.getUUID(), SettlementPermission.VIEW)
                    ? new Result(Status.EXISTING_TOWN, existing) : new Result(Status.UNAVAILABLE, Optional.empty());
        }
        if (!data.canEstablish(level.dimension().location(), center)) {
            return new Result(Status.TOO_CLOSE, Optional.empty());
        }
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.discoverAt(level, center).flatMap(registry::canonicalRecord).orElse(null);
        if (village != null && (registry.isManaged(village.id()) || village.archived())) {
            return new Result(Status.UNAVAILABLE, Optional.empty());
        }
        if (village != null && !village.residents().isEmpty()) {
            return new Result(Status.INDEPENDENT_VILLAGE, Optional.empty());
        }
        var id = village == null ? registry.create(level.getGameTime(), level.dimension().location(), center, "") : village.id();
        var created = data.establish(registry, id, player.getUUID(), center, level.getGameTime());
        created.ifPresent(town -> com.jvn.villagerretaliation.villagelife.VillageOccasionSavedData.get(level).schedule(
                level, town.id(), com.jvn.villagerretaliation.villagelife.VillageOccasionSavedData.Kind.FOUNDING,
                "founding:" + town.foundedAt()));
        return new Result(created.isPresent() ? Status.FOUNDED : Status.UNAVAILABLE, created);
    }

    public enum Status {
        FOUNDED,
        EXISTING_TOWN,
        INDEPENDENT_VILLAGE,
        TOO_CLOSE,
        UNAVAILABLE
    }

    public record Result(Status status, Optional<SettlementRecord> town) {
    }
}
