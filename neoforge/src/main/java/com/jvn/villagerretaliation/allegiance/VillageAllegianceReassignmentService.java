package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/** Residency, community-consent, and confirmation rules for changing a villager's home. */
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
        if (!hasClaimInVillage(level, villager, village.id())) {
            clearResidency(villager);
            return Eligibility.denied(Reason.NO_CLAIMED_HOME_OR_JOB, REQUIRED_RESIDENCY_TICKS);
        }
        long now = level.getGameTime();
        Residency residency = readResidency(villager).orElse(null);
        if (residency == null || !registry.canonical(residency.village()).filter(village.id()::equals).isPresent()
                || now < residency.sinceGameTime()) {
            residency = new Residency(village.id(), now);
            writeResidency(villager, residency);
        }
        long remaining = Math.max(0L, REQUIRED_RESIDENCY_TICKS - (now - residency.sinceGameTime()));
        if (remaining > 0L) {
            return Eligibility.denied(Reason.RESIDENCY_TIME, remaining);
        }
        VillagerReputationLevel reputation = VillagerReputationManager
                .getReputationSnapshot(level, villager, player.getUUID()).level();
        if (reputation.trustRank() < VillagerReputationLevel.REVERED.trustRank()) {
            return Eligibility.denied(Reason.INDIVIDUAL_TRUST, 0L);
        }
        VillageNamingService.TrustGate community = VillageNamingService.trustGate(level, player, village);
        if (!community.allowed()) {
            return new Eligibility(false, Reason.COMMUNITY_TRUST, 0L,
                    community.trustedResidents(), community.requiredResidents());
        }
        return new Eligibility(true, Reason.READY, 0L,
                community.trustedResidents(), community.requiredResidents());
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

    public static void clearRuntimeState() {
        CONFIRMATIONS.clear();
    }

    public static String describe(Eligibility eligibility) {
        return switch (eligibility.reason()) {
            case READY -> "Ready; ask twice to confirm the change of home.";
            case INVALID -> "Reassignment is not available right now.";
            case NO_ACTIVE_VILLAGE -> "There is no active village here to join.";
            case ALREADY_HOME -> "This is already the villager's home.";
            case INDIVIDUAL_TRUST -> "The villager must trust you at Revered or Royalty standing.";
            case NO_CLAIMED_HOME_OR_JOB -> "The villager needs a claimed bed or workstation in this village.";
            case RESIDENCY_TIME -> {
                long days = Math.max(1L, (eligibility.remainingResidencyTicks() + 23_999L) / 24_000L);
                yield "The villager needs about " + days + " more resident day" + (days == 1L ? "." : "s.");
            }
            case COMMUNITY_TRUST -> "The village requires Revered standing with "
                    + eligibility.requiredResidents() + " residents; you currently have "
                    + eligibility.trustedResidents() + ".";
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
        villager.getPersistentData().remove(RESIDENCY_TAG);
    }

    private static boolean hasClaimInVillage(
            ServerLevel level,
            Villager villager,
            VillageAllegianceId target) {
        return memoryBelongsTo(level, villager, target, MemoryModuleType.HOME)
                || memoryBelongsTo(level, villager, target, MemoryModuleType.JOB_SITE);
    }

    private static boolean memoryBelongsTo(
            ServerLevel level,
            Villager villager,
            VillageAllegianceId target,
            MemoryModuleType<GlobalPos> memoryType) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return villager.getBrain().getMemory(memoryType)
                .filter(global -> global.dimension().equals(level.dimension()))
                .flatMap(global -> registry.peekAt(level, global.pos()))
                .flatMap(registry::canonical)
                .filter(target::equals)
                .isPresent();
    }

    public enum Reason {
        READY,
        INVALID,
        NO_ACTIVE_VILLAGE,
        ALREADY_HOME,
        INDIVIDUAL_TRUST,
        NO_CLAIMED_HOME_OR_JOB,
        RESIDENCY_TIME,
        COMMUNITY_TRUST
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
