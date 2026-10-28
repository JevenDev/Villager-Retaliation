package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mount.VillagerMountAssignmentService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryMenu;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class PartyVillagerContractService {
    public static final int DAILY_EMERALD_COST = 32;
    public static final int INITIAL_PAID_DAYS = 1;
    private static final String PARTY_ID_TAG = "VillagerRetaliationPartyId";
    private static final String PARTY_CONTRACT_ID_TAG = "VillagerRetaliationPartyContractId";
    private static final String PARTY_RECRUITER_ID_TAG = "VillagerRetaliationPartyRecruiterId";
    private static final String EXPIRED_PARTY_ID_TAG = "VillagerRetaliationExpiredPartyId";
    private static final long EXPIRATION_SCAN_INTERVAL_TICKS = 20L;
    private static long nextExpirationScanGameTime = Long.MIN_VALUE;

    private PartyVillagerContractService() {
    }

    public static ContractResult recruit(ServerPlayer player, Villager villager) {
        return recruit(player, villager, null);
    }

    static ContractResult recruitTemporary(ServerPlayer player, Villager villager, net.minecraft.nbt.CompoundTag service) {
        return recruit(player, villager, service);
    }

    private static ContractResult recruit(ServerPlayer player, Villager villager, net.minecraft.nbt.CompoundTag service) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        if (player.serverLevel() != level || !villager.isAlive() || villager.isBaby()
                || !VillagerInteractionService.canUseInteractionSystem(player, villager)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        PartyRecord currentParty = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        if (currentParty != null && currentParty.villagers().size() >= PartyService.MAX_VILLAGERS) {
            return ContractResult.failure("villagerretaliation.party.error.villager_limit");
        }
        if (PartyService.getPartyForVillager(level, villager.getUUID()).isPresent()) {
            return ContractResult.failure("villagerretaliation.party.error.villager_already_in_party");
        }
        if (HiredVillagerContractService.isHired(level, villager)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_already_hired");
        }
        if (HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, player)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        Optional<UUID> followingPlayer = VillagerRecruitmentService.followingPlayerId(villager);
        if (followingPlayer.isPresent() && !followingPlayer.get().equals(player.getUUID())) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        if (!VillagerRecruitmentService.canRecruit(level, villager, player)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        UUID companionOwner = CompanionLifecycleService.owner(level, villager.getUUID());
        if (companionOwner != null && !companionOwner.equals(player.getUUID())) {
            return ContractResult.failure("villagerretaliation.party.error.companion_owner_required");
        }
        if (com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(villager) || !CompanionLifecycleService.canPart(villager)) {
            return ContractResult.failure("villagerretaliation.party.error.companion_in_combat");
        }
        var personalState = CompanionSavedData.get(level).find(villager.getUUID());
        if (personalState != null && personalState.getBoolean("TrustSuspended") && !CompanionRelationshipService.eligible(level, villager, player)) {
            return ContractResult.failure("villagerretaliation.party.error.companion_trust_required");
        }
        int recruitmentCost = service == null ? CompanionRelationshipService.recruitmentCost(level, villager, player) : 0;
        if (VillagerCurrencyPayment.count(player) < recruitmentCost) {
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }

        long now = level.getServer().overworld().getGameTime();
        UUID contractId = UUID.randomUUID();
        PartyVillagerRecord record = new PartyVillagerRecord(
                villager.getUUID(),
                player.getUUID(),
                contractId,
                currentParty == null ? 0 : currentParty.nextRecruitmentOrder(),
                PartyCommandMode.FOLLOW,
                null,
                null,
                now,
                VillagerContractTime.endAfterDays(now, INITIAL_PAID_DAYS),
                INITIAL_PAID_DAYS,
                recruitmentCost,
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                level.dimension().location(),
                villager.blockPosition()
        );
        record.setCachedGender(VillagerPresetNameRegistry.resolveGender(villager).serializedName());
        if (currentParty != null) {
            record.setCombatMode(currentParty.combatMode());
            record.setAttackMode(currentParty.attackMode());
        }

        PartyService.PartyResult membership = PartyService.addVillager(level, player.getUUID(), record, now);
        if (!membership.success()) {
            return ContractResult.failure(membership.messageKey());
        }
        if (!VillagerCurrencyPayment.tryRemove(player, recruitmentCost)) {
            PartyService.removeVillager(level, villager.getUUID());
            if (currentParty == null && membership.partyId() != null) {
                PartyService.deleteParty(level, membership.partyId());
            }
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }

        VillagerWalletService.addCurrency(villager, recruitmentCost);
        var personal = CompanionSavedData.get(level).getOrCreate(villager.getUUID());
        if (service == null) {
            personal.remove("ActiveService");
        } else {
            var term = service.copy();
            term.putLong("End", now + service.getInt("Duration"));
            personal.put("ActiveService", term);
        }
        attachEntityState(level, villager, membership.partyId(), record);
        HiredVillagerContractService.takeOverJobInventoryOverflow(villager);
        var companion = CompanionSavedData.get(level).find(villager.getUUID());
        companion.putBoolean("Returning", false);
        companion.putString("Status", "Active");
        companion.putBoolean("TrustSuspended", false);
        companion.putInt("BetrayalStrikes", 0);
        CompanionSavedData.get(level).setDirty();
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contractId);
        VillagerRecruitmentService.applyPartyFollowing(level, villager, player);
        com.jvn.villagerretaliation.social.VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
        PartySyncService.syncParty(level.getServer(), membership.partyId());
        return ContractResult.success(
                "villagerretaliation.party.villager_recruited",
                membership.partyId(),
                record,
                0,
                recruitmentCost);
    }

    public static ContractResult extend(ServerPlayer player, Villager villager, int requestedDays) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) {
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (!CompanionAuthority.canManage(player.getUUID(), party, record)) {
            return ContractResult.failure("villagerretaliation.party.error.not_in_party");
        }
        long now = level.getServer().overworld().getGameTime();
        if (VillagerContractTime.isExpired(now, record.contractEndGameTime())) {
            expire(level.getServer(), party, record);
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        if (record.contractEndGameTime() == Long.MAX_VALUE) {
            return ContractResult.failure("villagerretaliation.party.error.companion_permanent");
        }
        int extensionDays = record.availableExtensionDays(now, requestedDays);
        if (requestedDays <= 0 || extensionDays != requestedDays) {
            return ContractResult.failure("villagerretaliation.party.error.contract_maximum");
        }
        int cost = Math.multiplyExact(extensionDays, DAILY_EMERALD_COST);
        if (!VillagerCurrencyPayment.tryRemove(player, cost)) {
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }
        VillagerWalletService.addCurrency(villager, cost);
        long newEnd = VillagerContractTime.extendEnd(now, record.contractEndGameTime(), extensionDays);
        record.extend(newEnd, extensionDays, cost);
        PartyService.markChanged(level);
        villager.setPersistenceRequired();
        PartySyncService.syncParty(level.getServer(), party.id());
        return ContractResult.success(
                "villagerretaliation.party.contract_extended",
                party.id(),
                record,
                extensionDays,
                cost);
    }

    public static ContractResult setFollowing(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        var companionState = CompanionSavedData.get(context.level()).find(villager.getUUID());
        if (companionState != null && companionState.getBoolean("TrustSuspended")
                && (!context.record().recruiterId().equals(player.getUUID())
                || !CompanionRelationshipService.eligible(context.level(), villager, player))) {
            return ContractResult.failure("villagerretaliation.party.error.companion_trust_required");
        }
        if (companionState != null) {
            companionState.putBoolean("TrustSuspended", false);
            companionState.putBoolean("Returning", false);
            companionState.putString("Status", "Active");
            CompanionSavedData.get(context.level()).setDirty();
        }
        context.record().assignCommander(player.getUUID());
        PartyCommandService.follow(context.level(), context.record());
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
        return ContractResult.success("villagerretaliation.party.villager_following", context.party().id(), context.record(), 0, 0);
    }

    public static ContractResult setStaying(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        BlockPos anchor = villager.blockPosition();
        PartyCommandService.stay(context.level(), villager, context.record(), anchor);
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
        return ContractResult.success("villagerretaliation.party.villager_staying", context.party().id(), context.record(), 0, 0);
    }

    public static ContractResult dismiss(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        if (!CompanionAuthority.canManage(player.getUUID(), context.party(), context.record())) {
            return ContractResult.failure("villagerretaliation.party.error.companion_owner_required");
        }
        if (!CompanionLifecycleService.canPart(villager)) {
            return ContractResult.failure("villagerretaliation.party.error.companion_in_combat");
        }
        PartyVillagerRecord removed = PartyService.removeVillager(context.level(), villager.getUUID());
        if (removed == null) {
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        villager.getPersistentData().putUUID(EXPIRED_PARTY_ID_TAG, context.party().id());
        rememberJobInventoryClaim(context.level(), villager, removed.contractId(), removed.recruiterId());
        cleanupEntity(villager);
        CompanionSavedData.get(context.level()).find(villager.getUUID()).remove("ActiveService");
        CompanionLifecycleService.beginDeparture(context.level(), villager.getUUID(), "Said goodbye to owner");
        closeJobInventories(context.level().getServer(), villager.getId());
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
        return ContractResult.success("villagerretaliation.party.villager_dismissed", context.party().id(), removed, 0, 0);
    }

    public static ContractResult cycleCombatMode(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        context.record().setCombatMode(context.record().combatMode().next());
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(villager);
        return settingsChanged(context, "villagerretaliation.party.villager_settings_updated");
    }

    public static ContractResult cycleAttackMode(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        context.record().setAttackMode(context.record().attackMode().next());
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(villager);
        return settingsChanged(context, "villagerretaliation.party.villager_settings_updated");
    }

    public static ContractResult cycleDropCollectionMode(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        context.record().setDropCollectionMode(context.record().dropCollectionMode().next());
        return settingsChanged(context, "villagerretaliation.party.villager_settings_updated");
    }

    public static boolean canAccessJobInventory(ServerLevel level, Villager villager, ServerPlayer player) {
        if (level == null || villager == null || player == null || !villager.isAlive() || villager.isBaby()) {
            return false;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (!isAuthorizedInventoryUser(player, party, record)) {
            return false;
        }
        long now = level.getServer().overworld().getGameTime();
        return !VillagerContractTime.isExpired(now, record.contractEndGameTime());
    }

    public static Optional<UUID> currentContractId(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return Optional.empty();
        }
        long now = level.getServer().overworld().getGameTime();
        return PartyService.getPartyForVillager(level, villager.getUUID())
                .map(party -> party.villager(villager.getUUID()))
                .filter(record -> !VillagerContractTime.isExpired(now, record.contractEndGameTime()))
                .map(PartyVillagerRecord::contractId);
    }

    public static boolean hasPartyEntityReference(Villager villager) {
        return villager != null && villager.getPersistentData().hasUUID(PARTY_ID_TAG);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child == null) return;
        child.getPersistentData().remove(PARTY_ID_TAG);
        child.getPersistentData().remove(PARTY_CONTRACT_ID_TAG);
        child.getPersistentData().remove(PARTY_RECRUITER_ID_TAG);
        child.getPersistentData().remove(EXPIRED_PARTY_ID_TAG);
    }

    public static boolean hasExpiredContractWithParty(Villager villager, UUID partyId) {
        return villager != null
                && partyId != null
                && villager.getPersistentData().hasUUID(EXPIRED_PARTY_ID_TAG)
                && partyId.equals(villager.getPersistentData().getUUID(EXPIRED_PARTY_ID_TAG));
    }

    public static Optional<UUID> leaderId(ServerLevel level, Villager villager) {
        return villager == null
                ? Optional.empty()
                : PartyService.getPartyForVillager(level, villager.getUUID()).map(PartyRecord::leaderId);
    }

    public static boolean canRenewExpiredContract(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!isRetainedPartyInventory(level, villager, player) || !CompanionLifecycleService.canPart(villager)
                || !VillagerInteractionService.canUseInteractionSystem(player, villager)
                || CompanionRelationshipService.trustSuspended(level, villager.getUUID())
                && !CompanionRelationshipService.eligible(level, villager, player)) {
            return false;
        }
        UUID expiredPartyId = villager.getPersistentData().getUUID(EXPIRED_PARTY_ID_TAG);
        PartyRecord party = PartyService.getParty(level, expiredPartyId).orElse(null);
        return party != null
                && party.playerIds().contains(player.getUUID())
                && party.villagers().size() < PartyService.MAX_VILLAGERS
                && PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty()
                && !HiredVillagerContractService.isHired(level, villager);
    }

    public static boolean isRetainedPartyInventory(ServerLevel level, Villager villager, ServerPlayer player) {
        return level != null
                && villager != null
                && player != null
                && !villager.isBaby()
                && villager.isAlive()
                && villager.getPersistentData().hasUUID(EXPIRED_PARTY_ID_TAG)
                && HiredVillagerContractService.canAccessJobInventory(level, villager, player);
    }

    public static ContractResult renewExpired(ServerPlayer player, Villager villager, int requestedDays) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)
                || !canRenewExpiredContract(level, villager, player)) {
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        int days = VillagerContractTime.availableExtensionDays(
                level.getServer().overworld().getGameTime(),
                level.getServer().overworld().getGameTime(),
                requestedDays);
        if (requestedDays <= 0 || days != requestedDays) {
            return ContractResult.failure("villagerretaliation.party.error.contract_maximum");
        }
        int cost = CompanionRelationshipService.recruitmentCost(level, villager, player);
        if (VillagerCurrencyPayment.count(player) < cost) {
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }

        UUID partyId = villager.getPersistentData().getUUID(EXPIRED_PARTY_ID_TAG);
        PartyRecord party = PartyService.getParty(level, partyId).orElse(null);
        if (party == null) {
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        long now = level.getServer().overworld().getGameTime();
        UUID contractId = UUID.randomUUID();
        PartyVillagerRecord record = new PartyVillagerRecord(
                villager.getUUID(), player.getUUID(), contractId, party.nextRecruitmentOrder(),
                PartyCommandMode.FOLLOW, null, null, now,
                VillagerContractTime.endAfterDays(now, days), days, cost,
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager), level.dimension().location(), villager.blockPosition());
        record.setCachedGender(VillagerPresetNameRegistry.resolveGender(villager).serializedName());
        record.setCombatMode(party.combatMode());
        record.setAttackMode(party.attackMode());
        PartySavedData.get(level).addVillager(party, record);
        if (!VillagerCurrencyPayment.tryRemove(player, cost)) {
            PartyService.removeVillager(level, villager.getUUID());
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }
        VillagerWalletService.addCurrency(villager, cost);
        attachEntityState(level, villager, party.id(), record);
        HiredVillagerContractService.takeOverJobInventoryOverflow(villager);
        var companion = CompanionSavedData.get(level).find(villager.getUUID());
        companion.putBoolean("Returning", false);
        companion.putString("Status", "Active");
        companion.putBoolean("TrustSuspended", false);
        companion.putInt("BetrayalStrikes", 0);
        CompanionSavedData.get(level).setDirty();
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contractId);
        VillagerRecruitmentService.applyPartyFollowing(level, villager, player);
        com.jvn.villagerretaliation.social.VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
        PartySyncService.syncParty(level.getServer(), party.id());
        return ContractResult.success(
                "villagerretaliation.party.contract_extended", party.id(), record, days, cost);
    }

    public static boolean isActivePartyVillager(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return false;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        return record != null && !VillagerContractTime.isExpired(
                level.getServer().overworld().getGameTime(),
                record.contractEndGameTime());
    }

    public static OptionalLong getPartyEndGameTime(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return OptionalLong.empty();
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        return record == null || record.contractEndGameTime() == Long.MAX_VALUE || VillagerContractTime.isExpired(
                level.getServer().overworld().getGameTime(), record.contractEndGameTime())
                ? OptionalLong.empty()
                : OptionalLong.of(record.contractEndGameTime());
    }

    public static void onVillagerLoaded(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record == null) {
            if (hasPartyEntityReference(villager)) {
                rememberExpiredParty(villager);
                rememberJobInventoryClaimFromEntityState(level, villager);
                cleanupEntity(villager);
            }
            return;
        }
        long now = level.getServer().overworld().getGameTime();
        if (VillagerContractTime.isExpired(now, record.contractEndGameTime())) {
            expire(level.getServer(), party, record);
            return;
        }
        if (record.updateDisplay(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                VillagerPresetNameRegistry.resolveGender(villager).serializedName(),
                level.dimension().location(),
                villager.blockPosition())) {
            PartyService.markChanged(level);
        }
        attachEntityState(level, villager, party.id(), record);
        applyCommandState(level, villager, party, record);
        for (UUID playerId : party.playerIds()) {
            ServerPlayer member = level.getServer().getPlayerList().getPlayer(playerId);
            if (member != null) {
                com.jvn.villagerretaliation.quest.VillagerQuestService.attachPendingPartyQuests(member);
            }
        }
        PartySyncService.syncParty(level.getServer(), party.id());
    }

    public static void onVillagerUnloaded(Villager villager) {
        if (villager != null && villager.level() instanceof ServerLevel level) {
            PartyService.getPartyForVillager(level, villager.getUUID())
                    .ifPresent(party -> {
                        PartyVillagerRecord record = party.villager(villager.getUUID());
                        if (record != null) {
                            if (updateLastKnownLocation(record, villager)) {
                                PartyService.markChanged(level);
                            }
                        }
                        PartySyncService.syncPartyWithUnavailableVillager(
                                level.getServer(),
                                party.id(),
                                villager.getUUID());
                    });
        }
    }

    public static void onVillagerPermanentlyRemoved(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        if (party == null || PartyService.removeVillager(level, villager.getUUID()) == null) {
            return;
        }
        cleanupEntity(villager);
        closeJobInventories(level.getServer(), villager.getId());
        PartySyncService.syncParty(level.getServer(), party.id());
    }

    public static void onVillagerDeath(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        if (party == null) {
            return;
        }
        PartyVillagerRecord record = party.villager(villager.getUUID());
        if (record == null) {
            return;
        }
        updateLastKnownLocation(record, villager);
        PartyService.removeVillager(level, villager.getUUID());
        cleanupEntity(villager);
        closeJobInventories(level.getServer(), villager.getId());
        notifyLeader(level.getServer(), party, "villagerretaliation.party.villager_died", record);
        PartySyncService.syncParty(level.getServer(), party.id());
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (now < nextExpirationScanGameTime) {
            return;
        }
        nextExpirationScanGameTime = now + EXPIRATION_SCAN_INTERVAL_TICKS;
        CompanionLifecycleService.migrate(server);
        List<ExpiredContract> expired = new ArrayList<>();
        PartySavedData data = PartySavedData.get(server.overworld());
        for (PartyRecord party : data.partyRecords()) {
            for (PartyVillagerRecord villager : party.villagers()) {
                if (VillagerContractTime.isExpired(now, villager.contractEndGameTime())) {
                    expired.add(new ExpiredContract(party, villager));
                }
            }
        }
        for (ExpiredContract contract : expired) {
            expire(server, contract.party(), contract.villager());
        }
        var serviceParties = new ArrayList<PartyRecord>();
        data.partyRecords().forEach(serviceParties::add);
        for (PartyRecord party : serviceParties) {
            for (PartyVillagerRecord record : List.copyOf(party.villagers())) {
                CompanionServiceOffers.maintain(server.overworld(), party, record);
            }
        }
        PartyService.pruneExpiredInvitations(server);
    }

    static void endTemporaryService(ServerLevel level, PartyRecord party, PartyVillagerRecord record, Villager villager) {
        if (PartyService.removeVillager(level, record.villagerId()) == null) return;
        rememberJobInventoryClaim(level, villager, record.contractId(), record.recruiterId());
        cleanupEntity(villager);
        CompanionSavedData.get(level).find(record.villagerId()).remove("ActiveService");
        CompanionLifecycleService.beginDeparture(level, record.villagerId(), "Temporary service completed");
        closeJobInventories(level.getServer(), villager.getId());
        var owner = level.getServer().getPlayerList().getPlayer(record.recruiterId());
        if (owner != null) owner.sendSystemMessage(Component.literal(villager.getName().getString() + " has finished their temporary service and is returning home."));
        PartySyncService.syncParty(level.getServer(), party.id());
    }

    public static List<PartyVillagerRecord> disband(ServerPlayer leader) {
        if (leader == null) {
            return List.of();
        }
        ServerLevel level = leader.serverLevel();
        PartyRecord party = PartyService.getPartyForPlayer(level, leader.getUUID()).orElse(null);
        if (party == null || !party.leaderId().equals(leader.getUUID())) {
            return List.of();
        }
        List<PartyVillagerRecord> villagers = List.copyOf(party.villagers());
        for (PartyVillagerRecord record : villagers) {
            if (!record.recruiterId().equals(leader.getUUID())) continue;
            Villager loaded = VillagerEntityResolver.loaded(level.getServer(), record.villagerId());
            if (loaded == null) {
                leader.sendSystemMessage(Component.translatable("villagerretaliation.party.error.villager_unavailable"));
                return List.of();
            }
            if (!CompanionLifecycleService.canPart(loaded)) {
                leader.sendSystemMessage(Component.translatable("villagerretaliation.party.error.companion_in_combat"));
                return List.of();
            }
        }
        PartySavedData.get(level).removeParty(party.id());
        var personalParties = new java.util.LinkedHashMap<UUID, PartyRecord>();
        for (PartyVillagerRecord record : villagers) {
            if (!record.recruiterId().equals(leader.getUUID())) {
                var personal = personalParties.computeIfAbsent(record.recruiterId(), owner ->
                        PartySavedData.get(level).createParty(owner, level.getServer().overworld().getGameTime()));
                PartyCommandService.reassignCommander(level, record, record.recruiterId());
                PartySavedData.get(level).addVillager(personal, record);
                continue;
            }
            VillagerMountAssignmentService.clearAssignment(level, record.villagerId());
            var state = CompanionSavedData.get(level).find(record.villagerId());
            if (state != null) state.remove("ActiveService");
            CompanionLifecycleService.beginDeparture(level, record.villagerId(), "Party disbanded");
            Villager loaded = VillagerEntityResolver.loaded(level.getServer(), record.villagerId());
            if (loaded != null) {
                rememberJobInventoryClaimFromEntityState((ServerLevel) loaded.level(), loaded);
                cleanupEntity(loaded);
                closeJobInventories(level.getServer(), loaded.getId());
            }
        }
        for (var personal : personalParties.values()) {
            refreshCommandOwnership(level.getServer(), personal);
            PartySyncService.syncParty(level.getServer(), personal.id());
        }
        return villagers;
    }

    public static void clearRuntimeState() {
        nextExpirationScanGameTime = Long.MIN_VALUE;
        com.jvn.villagerretaliation.quest.QuestContributionService.clearRuntimeState();
    }

    private static PartyVillagerContext authorizedContext(ServerPlayer player, Villager villager) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)
                || player.serverLevel() != level || !VillagerInteractionService.canUseInteractionSystem(player, villager)) {
            return null;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (!isAuthorizedLeader(player, party, record)) {
            return null;
        }
        if (VillagerContractTime.isExpired(level.getServer().overworld().getGameTime(), record.contractEndGameTime())) {
            expire(level.getServer(), party, record);
            return null;
        }
        return new PartyVillagerContext(level, party, record);
    }

    private static boolean isAuthorizedLeader(ServerPlayer player, PartyRecord party, PartyVillagerRecord record) {
        return player != null && CompanionAuthority.canCommand(player.getUUID(), party, record);
    }

    private static ContractResult settingsChanged(PartyVillagerContext context, String messageKey) {
        PartyService.markChanged(context.level());
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
        return ContractResult.success(messageKey, context.party().id(), context.record(), 0, 0);
    }

    private static boolean isAuthorizedPartyMember(ServerPlayer player, PartyRecord party, PartyVillagerRecord record) {
        return player != null
                && party != null
                && record != null
                && party.playerIds().contains(player.getUUID());
    }

    private static boolean isAuthorizedInventoryUser(ServerPlayer player, PartyRecord party, PartyVillagerRecord record) {
        return player != null && CompanionAuthority.canEquip(player.getUUID(), party, record);
    }

    private static void expire(MinecraftServer server, PartyRecord party, PartyVillagerRecord record) {
        if (party == null || record == null) {
            return;
        }
        PartyVillagerRecord removed = PartyService.removeVillager(server.overworld(), record.villagerId());
        if (removed == null) {
            return;
        }
        Villager loaded = VillagerEntityResolver.loaded(server, record.villagerId());
        if (loaded != null) {
            updateLastKnownLocation(record, loaded);
            loaded.getPersistentData().putUUID(EXPIRED_PARTY_ID_TAG, party.id());
            rememberJobInventoryClaim((ServerLevel) loaded.level(), loaded, record.contractId(), record.recruiterId());
            cleanupEntity(loaded);
            closeJobInventories(server, loaded.getId());
        }
        notifyLeader(server, party, "villagerretaliation.party.contract_expired", record);
        PartySyncService.syncParty(server, party.id());
    }

    private static void attachEntityState(ServerLevel level, Villager villager, UUID partyId, PartyVillagerRecord record) {
        villager.getPersistentData().remove(EXPIRED_PARTY_ID_TAG);
        villager.getPersistentData().putUUID(PARTY_ID_TAG, partyId);
        villager.getPersistentData().putUUID(PARTY_CONTRACT_ID_TAG, record.contractId());
        villager.getPersistentData().putUUID(PARTY_RECRUITER_ID_TAG, record.recruiterId());
        villager.setPersistenceRequired();
        CompanionLifecycleService.associate(level, villager, record);
        VillagerBehaviorSuppressionPolicy.enforce(level, villager);
    }

    private static void applyCommandState(
            ServerLevel level,
            Villager villager,
            PartyRecord party,
            PartyVillagerRecord record) {
        var companionState = CompanionSavedData.get(level).find(villager.getUUID());
        if (companionState != null && (companionState.getBoolean("Returning") || companionState.getString("Status").equals("At home"))) return;
        if (record.moveToReturnCommanderId() != null && !record.moveToHolding()) {
            return;
        }
        if (record.commandMode() == PartyCommandMode.STAY) {
            ResourceLocation dimension = record.stayDimension();
            if (dimension != null && dimension.equals(level.dimension().location())) {
                VillagerRecruitmentService.applyPartyStay(level, villager, record.commanderId(), record.stayPosition());
            } else {
                VillagerRecruitmentService.clearPartyFollowing(villager);
            }
        } else {
            VillagerRecruitmentService.applyPartyFollowing(level, villager, record.commanderId());
        }
    }

    static void refreshCommandOwnership(MinecraftServer server, PartyRecord party) {
        if (server == null || party == null) {
            return;
        }
        for (PartyVillagerRecord record : party.villagers()) {
            Villager villager = VillagerEntityResolver.loaded(server, record.villagerId());
            if (villager != null && villager.level() instanceof ServerLevel level) {
                attachEntityState(level, villager, party.id(), record);
                applyCommandState(level, villager, party, record);
            }
        }
    }

    public static void refreshTransferredOwnership(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record != null) {
            attachEntityState(level, villager, party.id(), record);
            applyCommandState(level, villager, party, record);
            closeJobInventories(level.getServer(), villager.getId());
        }
    }

    private static void cleanupEntity(Villager villager) {
        VillagerRecruitmentService.clearPartyFollowing(villager);
        villager.getPersistentData().remove(PARTY_ID_TAG);
        villager.getPersistentData().remove(PARTY_CONTRACT_ID_TAG);
        villager.getPersistentData().remove(PARTY_RECRUITER_ID_TAG);
        villager.setPersistenceRequired();
        if (villager.level() instanceof ServerLevel level) {
            VillagerBehaviorSuppressionPolicy.restoreAfterRelease(level, villager);
        }
    }

    /** Permanently releases a recruited villager who defends their home from a player raid. */
    public static boolean releaseForHomeVillageRaid(MinecraftServer server, UUID villagerId) {
        if (server == null || villagerId == null) {
            return false;
        }
        ServerLevel storageLevel = server.overworld();
        PartyRecord party = PartyService.getPartyForVillager(storageLevel, villagerId).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villagerId);
        if (party == null || PartyService.removeVillager(storageLevel, villagerId) == null) {
            return false;
        }
        var companionData = CompanionSavedData.get(storageLevel);
        var personal = companionData.find(villagerId);
        if (personal != null) {
            personal.putBoolean("TrustSuspended", true);
            personal.putString("Status", "At home");
            personal.putString("Reason", "Defending home after owner betrayal");
            companionData.setDirty();
        }
        Villager loaded = VillagerEntityResolver.loaded(server, villagerId);
        if (loaded != null) {
            if (record != null) {
                rememberJobInventoryClaim(
                        (ServerLevel) loaded.level(), loaded, record.contractId(), record.recruiterId());
            }
            cleanupEntity(loaded);
            closeJobInventories(server, loaded.getId());
        }
        PartySyncService.syncParty(server, party.id());
        return true;
    }

    private static void rememberExpiredParty(Villager villager) {
        if (villager.getPersistentData().hasUUID(PARTY_ID_TAG)) {
            villager.getPersistentData().putUUID(
                    EXPIRED_PARTY_ID_TAG,
                    villager.getPersistentData().getUUID(PARTY_ID_TAG));
        }
    }

    private static void rememberJobInventoryClaimFromEntityState(ServerLevel level, Villager villager) {
        if (villager.getPersistentData().hasUUID(PARTY_CONTRACT_ID_TAG)
                && villager.getPersistentData().hasUUID(PARTY_RECRUITER_ID_TAG)) {
            rememberJobInventoryClaim(
                    level,
                    villager,
                    villager.getPersistentData().getUUID(PARTY_CONTRACT_ID_TAG),
                    villager.getPersistentData().getUUID(PARTY_RECRUITER_ID_TAG));
        }
    }

    private static void rememberJobInventoryClaim(
            ServerLevel level,
            Villager villager,
            UUID contractId,
            UUID recruiterId) {
        HiredVillagerContractService.rememberJobInventoryOverflowClaim(
                level, villager, contractId, recruiterId);
    }

    private static void closeJobInventories(MinecraftServer server, int villagerEntityId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof VillagerInventoryMenu menu
                    && menu.isJobInventory()
                    && menu.villagerEntityId() == villagerEntityId) {
                player.closeContainer();
            }
        }
    }

    private static void notifyLeader(
            MinecraftServer server,
            PartyRecord party,
            String messageKey,
            PartyVillagerRecord record) {
        ServerPlayer leader = server.getPlayerList().getPlayer(party.leaderId());
        if (leader != null) {
            leader.sendSystemMessage(alertMessage(messageKey, record));
        }
    }

    static Component alertMessage(String messageKey, PartyVillagerRecord record) {
        String name = record.cachedName().isBlank() ? "Unknown villager" : record.cachedName();
        Component profession = record.cachedProfession().isBlank()
                ? Component.translatable("villagerretaliation.gui.profession.unemployed")
                : Component.translatable(record.cachedProfession());
        String dimension = record.lastKnownDimension() == null
                ? "unknown dimension"
                : record.lastKnownDimension().toString();
        BlockPos position = record.lastKnownPosition();
        String coordinates = position == null
                ? "unknown coordinates"
                : position.getX() + ", " + position.getY() + ", " + position.getZ();
        return Component.translatable(messageKey, name, profession, dimension, coordinates);
    }

    private static boolean updateLastKnownLocation(PartyVillagerRecord record, Villager villager) {
        return record.updateDisplay(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                VillagerPresetNameRegistry.resolveGender(villager).serializedName(),
                villager.level().dimension().location(),
                villager.blockPosition());
    }

    private static String professionTranslationKey(Villager villager) {
        return VillagerProfessionUtil.translationKey(
                villager.getVillagerData().getProfession(),
                "villagerretaliation.gui.profession.unemployed");
    }

    public record ContractResult(
            boolean success,
            String messageKey,
            UUID partyId,
            PartyVillagerRecord record,
            int days,
            int emeraldCost) {
        static ContractResult success(
                String messageKey,
                UUID partyId,
                PartyVillagerRecord record,
                int days,
                int emeraldCost) {
            return new ContractResult(true, messageKey, partyId, record, days, emeraldCost);
        }

        static ContractResult failure(String messageKey) {
            return new ContractResult(false, messageKey, null, null, 0, 0);
        }
    }

    private record PartyVillagerContext(ServerLevel level, PartyRecord party, PartyVillagerRecord record) {
    }

    private record ExpiredContract(PartyRecord party, PartyVillagerRecord villager) {
    }
}
