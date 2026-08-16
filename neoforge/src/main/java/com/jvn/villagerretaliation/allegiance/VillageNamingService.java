package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import com.jvn.villagerretaliation.network.VillageRenameRequestPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        if (player == null || payload == null || player.distanceToSqr(
                payload.bellPosition().getX() + 0.5D,
                payload.bellPosition().getY() + 0.5D,
                payload.bellPosition().getZ() + 0.5D) > MAX_DISTANCE_SQR) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos bell = payload.bellPosition();
        if (!(level.getBlockState(bell).getBlock() instanceof BellBlock)
                || !isHoldingBanner(player)) {
            return;
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> current = registry.peekAt(level, bell);
        Optional<VillageAllegianceId> requested = registry.canonical(payload.villageId());
        if (current.isEmpty() || requested.isEmpty() || !current.get().equals(requested.get())) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.village_naming.error.village_changed"));
            return;
        }
        VillageAllegianceRegistrySavedData.AllegianceRecord village = registry
                .canonicalRecord(current.get()).orElse(null);
        if (village == null || village.lifecycleState() != VillageLifecycleState.ACTIVE) {
            return;
        }
        TrustGate gate = trustGate(level, player, village);
        if (!gate.allowed()) {
            player.sendSystemMessage(Component.translatable(
                    "villagerretaliation.village_naming.error.insufficient_standing", gate.requiredResidents()));
            return;
        }
        Optional<String> validName = VillageAllegianceRegistrySavedData.validateVillageName(payload.name());
        if (validName.isEmpty()) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.village_naming.error.invalid_name"));
            return;
        }
        if (!registry.rename(village.id(), validName.get())) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.village_naming.error.name_in_use"));
            return;
        }
        player.sendSystemMessage(Component.translatable("villagerretaliation.village_naming.renamed", validName.get()));
    }

    public static TrustGate trustGate(
            ServerLevel level,
            ServerPlayer player,
        VillageAllegianceRegistrySavedData.AllegianceRecord village) {
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
