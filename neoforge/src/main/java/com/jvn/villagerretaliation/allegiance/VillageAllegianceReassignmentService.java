package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Trust, party-authority, residency tracking, and confirmation rules for changing a villager's home. */
public final class VillageAllegianceReassignmentService {
    private static final String RESIDENCY_TAG = "VillagerRetaliationVillageResidency";
    private static final String VILLAGE = "Village";
    private static final String SINCE = "SinceGameTime";
    public static final long REQUIRED_RESIDENCY_TICKS = 24_000L;
    private static final long CONFIRMATION_TICKS = 600L;
    private static final Map<ConfirmationKey, PendingConfirmation> CONFIRMATIONS = new HashMap<>();

    private VillageAllegianceReassignmentService() {
    }

    public static Eligibility eligibility(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            VillageAllegianceId target) {
        if (level == null || player == null || villager == null || target == null || villager.isBaby()) {
            return Eligibility.denied(Reason.INVALID, 0L);
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceRegistrySavedData.AllegianceRecord village = registry.canonicalRecord(target).orElse(null);
        if (village == null || village.lifecycleState() != VillageLifecycleState.ACTIVE) {
            return Eligibility.denied(Reason.NO_ACTIVE_VILLAGE, 0L);
        }
        if (VillageAllegianceApi.canonicalPrimary(level, villager).filter(village.id()::equals).isPresent()) {
            return Eligibility.denied(Reason.ALREADY_HOME, 0L);
        }
        if (PartyService.getPartyForVillager(level, villager.getUUID()).isPresent()
                && !PartyService.arePlayerAndVillagerInSameParty(
                        level, player.getUUID(), villager.getUUID())) {
            return Eligibility.denied(Reason.OUTSIDE_PARTY, 0L);
        }
        VillagerReputationLevel reputation = VillagerReputationManager
                .getReputationSnapshot(level, villager, player.getUUID()).level();
        if (reputation.trustRank() < VillagerReputationLevel.REVERED.trustRank()) {
            return Eligibility.denied(Reason.INDIVIDUAL_TRUST, 0L);
        }
        return new Eligibility(true, Reason.READY, 0L, 0, 0);
    }

    /** Returns true only on a repeated request for the same villager and target before expiry. */
    public static boolean confirmOrArm(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            VillageAllegianceId target) {
        long now = level.getGameTime();
        CONFIRMATIONS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        ConfirmationKey key = new ConfirmationKey(player.getUUID(), villager.getUUID());
        PendingConfirmation pending = CONFIRMATIONS.get(key);
        if (pending != null && pending.target().equals(target) && pending.expiresAt() >= now) {
            CONFIRMATIONS.remove(key);
            return true;
        }
        CONFIRMATIONS.put(key, new PendingConfirmation(target, now + CONFIRMATION_TICKS));
        return false;
    }

    public static void complete(Villager villager) {
        if (villager == null) {
            return;
        }
        clearResidency(villager);
        CONFIRMATIONS.keySet().removeIf(key -> key.villagerId().equals(villager.getUUID()));
    }

    public static void resetResidency(Villager villager) {
        if (villager != null) {
            clearResidency(villager);
        }
    }

    public static void clearRuntimeState() {
        CONFIRMATIONS.clear();
    }

    public static String describe(Eligibility eligibility) {
        return switch (eligibility.reason()) {
            case READY -> "I trust you enough to consider it. Ask me once more, and I will make this village my home.";
            case INVALID -> "I cannot make that decision right now.";
            case NO_ACTIVE_VILLAGE -> "There is no village here for me to call home.";
            case ALREADY_HOME -> "This is already my home village.";
            case INDIVIDUAL_TRUST -> "I do not know you well enough to let you choose my home.";
            case OUTSIDE_PARTY -> "I am traveling with my party. I will only take that order from someone in it.";
        };
    }

    public static Optional<Residency> readResidency(Villager villager) {
        if (villager == null || !villager.getPersistentData().contains(RESIDENCY_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = villager.getPersistentData().getCompound(RESIDENCY_TAG);
        if (!tag.hasUUID(VILLAGE)) {
            return Optional.empty();
        }
        return Optional.of(new Residency(
                new VillageAllegianceId(tag.getUUID(VILLAGE)), tag.getLong(SINCE)));
    }

    public static void writeResidency(Villager villager, Residency residency) {
        if (villager == null || residency == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putUUID(VILLAGE, residency.village().value());
        tag.putLong(SINCE, residency.sinceGameTime());
        villager.getPersistentData().put(RESIDENCY_TAG, tag);
    }

    private static void clearResidency(Villager villager) {
        if (villager.getPersistentData().contains(RESIDENCY_TAG)) {
            villager.getPersistentData().remove(RESIDENCY_TAG);
        }
    }

    public enum Reason {
        READY,
        INVALID,
        NO_ACTIVE_VILLAGE,
        ALREADY_HOME,
        INDIVIDUAL_TRUST,
        OUTSIDE_PARTY
    }

    public record Eligibility(
            boolean allowed,
            Reason reason,
            long remainingResidencyTicks,
            int trustedResidents,
            int requiredResidents) {
        private static Eligibility denied(Reason reason, long remaining) {
            return new Eligibility(false, reason, remaining, 0, 0);
        }
    }

    public record Residency(VillageAllegianceId village, long sinceGameTime) {
    }

    private record ConfirmationKey(UUID playerId, UUID villagerId) {
    }

    private record PendingConfirmation(VillageAllegianceId target, long expiresAt) {
    }
}
