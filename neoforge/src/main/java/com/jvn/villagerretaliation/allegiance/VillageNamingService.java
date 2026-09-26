package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillageRenameRequestPayload;
import com.jvn.villagerretaliation.network.VillageRenameResultPayload;
import com.jvn.villagerretaliation.network.VillageRenameResultPayload.Result;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageNamingService {
    private static final double MAX_DISTANCE_SQR = 8.0D * 8.0D;

    private VillageNamingService() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getItemStack().getItem() instanceof BannerItem)
                || !(level.getBlockState(event.getPos()).getBlock() instanceof BellBlock)) {
            return;
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> villageId = registry.discoverAt(level, event.getPos());
        if (villageId.isEmpty()) {
            return;
        }
        VillageAllegianceRegistrySavedData.AllegianceRecord village = registry
                .canonicalRecord(villageId.get()).orElse(null);
        if (village == null || village.lifecycleState() != VillageLifecycleState.ACTIVE) {
            return;
        }
        TrustGate gate = trustGate(level, player, village);
        try {
            PacketDistributor.sendToPlayer(player, new OpenVillageNamingPayload(
                    event.getPos().immutable(), village.id(), village.displayName(), gate.allowed(),
                    gate.trustedResidents(), gate.requiredResidents()));
        } catch (UnsupportedOperationException ignored) {
            // Mock players in server tests do not negotiate custom payloads.
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static void handleRename(ServerPlayer player, VillageRenameRequestPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        VillageRenameResultPayload result = rename(player, payload);
        PacketDistributor.sendToPlayer(player, result);
        if (result.result() == Result.SUCCESS) {
            player.sendSystemMessage(result.message());
        }
    }

    static VillageRenameResultPayload rename(ServerPlayer player, VillageRenameRequestPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos bell = payload.bellPosition();
        if (!player.isAlive() || player.distanceToSqr(bell.getCenter()) > MAX_DISTANCE_SQR
                || !level.hasChunkAt(bell)) {
            return failure(payload, Result.TOO_FAR, 0);
        }
        if (!ServerboundRequestLimiter.tryAcquire(player, VillageRenameRequestPayload.TYPE.id(), 20L)) {
            return failure(payload, Result.TRY_AGAIN, 0);
        }
        if (!(level.getBlockState(bell).getBlock() instanceof BellBlock)) {
            return failure(payload, Result.VILLAGE_CHANGED, 0);
        }
        if (!isHoldingBanner(player)) {
            return failure(payload, Result.BANNER_REQUIRED, 0);
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> current = registry.peekAt(level, bell);
        Optional<VillageAllegianceId> requested = registry.canonical(payload.villageId());
        if (current.isEmpty() || requested.isEmpty() || !current.get().equals(requested.get())) {
            return failure(payload, Result.VILLAGE_CHANGED, 0);
        }
        VillageAllegianceRegistrySavedData.AllegianceRecord village = registry
                .canonicalRecord(current.get()).orElse(null);
        if (village == null || village.lifecycleState() != VillageLifecycleState.ACTIVE) {
            return failure(payload, Result.VILLAGE_CHANGED, 0);
        }
        TrustGate gate = trustGate(level, player, village);
        if (!gate.allowed()) {
            return failure(payload, Result.INSUFFICIENT_STANDING, gate.requiredResidents());
        }
        Optional<String> validName = VillageAllegianceRegistrySavedData.validateVillageName(payload.name());
        if (validName.isEmpty()) {
            return failure(payload, Result.INVALID_NAME, 0);
        }
        if (!registry.rename(village.id(), validName.get())) {
            return failure(payload, Result.NAME_IN_USE, 0);
        }
        return new VillageRenameResultPayload(payload.requestId(), Result.SUCCESS, validName.get(),
                gate.requiredResidents());
    }

    private static VillageRenameResultPayload failure(VillageRenameRequestPayload payload, Result result,
                                                      int requiredResidents) {
        return new VillageRenameResultPayload(payload.requestId(), result, "", requiredResidents);
    }

    public static TrustGate trustGate(
            ServerLevel level,
            ServerPlayer player,
        VillageAllegianceRegistrySavedData.AllegianceRecord village) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        if (registry.isManaged(village.id())) {
            boolean allowed = com.jvn.villagerretaliation.settlement.SettlementSavedData.get(level)
                    .get(village.id())
                    .map(town -> town.permits(player.getUUID(),
                            com.jvn.villagerretaliation.settlement.SettlementPermission.BUILD))
                    .orElse(false);
            return new TrustGate(allowed, 0, 0);
        }
        var activeAdults = village.activeAdultResidents(level.getGameTime());
        int adults = activeAdults.size();
        int trusted = 0;
        VillagerReputationSavedData reputation = VillagerReputationSavedData.get(level);
        for (VillageAllegianceRegistrySavedData.ResidentRecord resident : activeAdults) {
            VillagerReputationSavedData.ReputationEntry entry = reputation.get(resident.id(), player.getUUID());
            if (entry != null && VillagerReputationLevel.fromReputation(entry.reputation()).trustRank()
                    >= VillagerReputationLevel.REVERED.trustRank()) {
                trusted++;
            }
        }
        boolean operator = player.getServer().getPlayerList().isOp(player.getGameProfile());
        return evaluateTrustGate(adults, trusted, operator);
    }

    public static TrustGate evaluateTrustGate(int adultResidents, int trustedResidents, boolean operator) {
        int adults = Math.max(0, adultResidents);
        int trusted = Math.clamp(trustedResidents, 0, adults);
        int required = (adults + 1) / 2;
        return new TrustGate(operator || adults > 0 && trusted >= required, trusted, required);
    }

    private static boolean isHoldingBanner(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof BannerItem
                || player.getOffhandItem().getItem() instanceof BannerItem;
    }

    public record TrustGate(boolean allowed, int trustedResidents, int requiredResidents) {
    }
}
