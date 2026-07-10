package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryMenu;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class PartyVillagerContractService {
    public static final int DAILY_EMERALD_COST = 32;
    public static final int INITIAL_PAID_DAYS = 1;
    private static final String PARTY_ID_TAG = "VillagerRetaliationPartyId";
    private static final String PARTY_CONTRACT_ID_TAG = "VillagerRetaliationPartyContractId";
    private static final long EXPIRATION_SCAN_INTERVAL_TICKS = 20L;
    private static long nextExpirationScanGameTime = Long.MIN_VALUE;

    private PartyVillagerContractService() {
    }

    public static ContractResult recruit(ServerPlayer player, Villager villager) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        if (player.serverLevel() != level || !villager.isAlive() || villager.isBaby()
                || !VillagerInteractionService.canUseInteractionSystem(player, villager)) {
            return ContractResult.failure("villagerretaliation.party.error.villager_unavailable");
        }
        PartyRecord currentParty = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        if (currentParty != null && !currentParty.leaderId().equals(player.getUUID())) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
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
        if (VillagerCurrencyPayment.count(player) < DAILY_EMERALD_COST) {
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
                DAILY_EMERALD_COST,
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                level.dimension().location()
        );

        PartyService.PartyResult membership = PartyService.addVillager(level, player.getUUID(), record, now);
        if (!membership.success()) {
            return ContractResult.failure(membership.messageKey());
        }
        if (!VillagerCurrencyPayment.tryRemove(player, DAILY_EMERALD_COST)) {
            PartyService.removeVillager(level, villager.getUUID());
            if (currentParty == null && membership.partyId() != null) {
                PartyService.deleteParty(level, membership.partyId());
            }
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }

        attachEntityState(level, villager, membership.partyId(), record);
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contractId);
        VillagerRecruitmentService.applyPartyFollowing(level, villager, player);
        return ContractResult.success(
                "villagerretaliation.party.villager_recruited",
                membership.partyId(),
                record,
                INITIAL_PAID_DAYS,
                DAILY_EMERALD_COST);
    }

    public static ContractResult extend(ServerPlayer player, Villager villager, int requestedDays) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) {
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (!isAuthorizedLeader(player, party, record)) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        long now = level.getServer().overworld().getGameTime();
        if (VillagerContractTime.isExpired(now, record.contractEndGameTime())) {
            expire(level.getServer(), party, record);
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        int extensionDays = record.availableExtensionDays(now, requestedDays);
        if (requestedDays <= 0 || extensionDays != requestedDays) {
            return ContractResult.failure("villagerretaliation.party.error.contract_maximum");
        }
        int cost = Math.multiplyExact(extensionDays, DAILY_EMERALD_COST);
        if (!VillagerCurrencyPayment.tryRemove(player, cost)) {
            return ContractResult.failure("villagerretaliation.party.error.insufficient_emeralds");
        }
        long newEnd = VillagerContractTime.extendEnd(now, record.contractEndGameTime(), extensionDays);
        record.extend(newEnd, extensionDays, cost);
        PartyService.markChanged(level);
        villager.setPersistenceRequired();
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
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        context.record().setFollowing();
        PartyService.markChanged(context.level());
        VillagerRecruitmentService.applyPartyFollowing(context.level(), villager, player);
        return ContractResult.success("villagerretaliation.party.villager_following", context.party().id(), context.record(), 0, 0);
    }

    public static ContractResult setStaying(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        BlockPos anchor = villager.blockPosition();
        context.record().setStaying(context.level().dimension().location(), anchor);
        PartyService.markChanged(context.level());
        VillagerRecruitmentService.applyPartyStay(context.level(), villager, player);
        return ContractResult.success("villagerretaliation.party.villager_staying", context.party().id(), context.record(), 0, 0);
    }

    public static ContractResult dismiss(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        PartyVillagerRecord removed = PartyService.removeVillager(context.level(), villager.getUUID());
        if (removed == null) {
            return ContractResult.failure("villagerretaliation.party.error.contract_inactive");
        }
        cleanupEntity(villager);
        closeJobInventories(context.level().getServer(), villager.getId());
        return ContractResult.success("villagerretaliation.party.villager_dismissed", context.party().id(), removed, 0, 0);
    }

    public static boolean canAccessJobInventory(ServerLevel level, Villager villager, ServerPlayer player) {
        if (level == null || villager == null || player == null || !villager.isAlive() || villager.isBaby()) {
            return false;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (!isAuthorizedLeader(player, party, record)) {
            return false;
        }
        long now = level.getServer().overworld().getGameTime();
        return !VillagerContractTime.isExpired(now, record.contractEndGameTime());
    }

    public static Optional<UUID> currentContractId(ServerLevel level, Villager villager) {
        long now = level.getServer().overworld().getGameTime();
        return PartyService.getPartyForVillager(level, villager.getUUID())
                .map(party -> party.villager(villager.getUUID()))
                .filter(record -> !VillagerContractTime.isExpired(now, record.contractEndGameTime()))
                .map(PartyVillagerRecord::contractId);
    }

    public static boolean hasPartyEntityReference(Villager villager) {
        return villager != null && villager.getPersistentData().hasUUID(PARTY_ID_TAG);
    }

    public static Optional<UUID> leaderId(ServerLevel level, Villager villager) {
        return PartyService.getPartyForVillager(level, villager.getUUID()).map(PartyRecord::leaderId);
    }

    public static boolean isActivePartyVillager(ServerLevel level, Villager villager) {
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        return record != null && !VillagerContractTime.isExpired(
                level.getServer().overworld().getGameTime(),
                record.contractEndGameTime());
    }

    public static void onVillagerLoaded(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record == null) {
            if (hasPartyEntityReference(villager)) {
                cleanupEntity(villager);
            }
            return;
        }
        long now = level.getServer().overworld().getGameTime();
        if (VillagerContractTime.isExpired(now, record.contractEndGameTime())) {
            expire(level.getServer(), party, record);
            return;
        }
        record.updateDisplay(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
                level.dimension().location());
        PartyService.markChanged(level);
        attachEntityState(level, villager, party.id(), record);
        applyCommandState(level, villager, party, record);
    }

    public static void onVillagerDeath(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        if (party == null) {
            return;
        }
        PartyService.removeVillager(level, villager.getUUID());
        cleanupEntity(villager);
        closeJobInventories(level.getServer(), villager.getId());
        notifyLeader(level.getServer(), party, "villagerretaliation.party.villager_died");
    }

    public static void onServerTick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now < nextExpirationScanGameTime) {
            return;
        }
        nextExpirationScanGameTime = now + EXPIRATION_SCAN_INTERVAL_TICKS;
        List<ExpiredContract> expired = new ArrayList<>();
        PartySavedData data = PartySavedData.get(server.overworld());
        for (PartyRecord party : data.parties()) {
            for (PartyVillagerRecord villager : party.villagers()) {
                if (VillagerContractTime.isExpired(now, villager.contractEndGameTime())) {
                    expired.add(new ExpiredContract(party, villager));
                }
            }
        }
        for (ExpiredContract contract : expired) {
            expire(server, contract.party(), contract.villager());
        }
        PartyService.pruneExpiredInvitations(server);
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
        PartyService.deleteParty(level, party.id());
        for (PartyVillagerRecord record : villagers) {
            Villager loaded = findLoadedVillager(level.getServer(), record.villagerId());
            if (loaded != null) {
                cleanupEntity(loaded);
                closeJobInventories(level.getServer(), loaded.getId());
            }
        }
        return villagers;
    }

    public static void clearRuntimeState() {
        nextExpirationScanGameTime = Long.MIN_VALUE;
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
        return player != null
                && party != null
                && record != null
                && party.leaderId().equals(player.getUUID())
                && record.recruiterId().equals(player.getUUID())
                && party.playerIds().contains(player.getUUID());
    }

    private static void expire(MinecraftServer server, PartyRecord party, PartyVillagerRecord record) {
        if (party == null || record == null) {
            return;
        }
        PartyVillagerRecord removed = PartyService.removeVillager(server.overworld(), record.villagerId());
        if (removed == null) {
            return;
        }
        Villager loaded = findLoadedVillager(server, record.villagerId());
        if (loaded != null) {
            cleanupEntity(loaded);
            closeJobInventories(server, loaded.getId());
        }
        notifyLeader(server, party, "villagerretaliation.party.contract_expired");
    }

    private static void attachEntityState(ServerLevel level, Villager villager, UUID partyId, PartyVillagerRecord record) {
        villager.getPersistentData().putUUID(PARTY_ID_TAG, partyId);
        villager.getPersistentData().putUUID(PARTY_CONTRACT_ID_TAG, record.contractId());
        villager.setPersistenceRequired();
    }

    private static void applyCommandState(
            ServerLevel level,
            Villager villager,
            PartyRecord party,
            PartyVillagerRecord record) {
        if (record.commandMode() == PartyCommandMode.STAY) {
            ResourceLocation dimension = record.stayDimension();
            if (dimension != null && dimension.equals(level.dimension().location())) {
                VillagerRecruitmentService.applyPartyStay(level, villager, party.leaderId(), record.stayPosition());
            } else if (dimension == null || !dimension.equals(level.dimension().location())) {
                VillagerRecruitmentService.clearPartyFollowing(villager);
            }
        } else {
            VillagerRecruitmentService.applyPartyFollowing(level, villager, party.leaderId());
        }
    }

    private static void cleanupEntity(Villager villager) {
        VillagerRecruitmentService.clearPartyFollowing(villager);
        villager.getPersistentData().remove(PARTY_ID_TAG);
        villager.getPersistentData().remove(PARTY_CONTRACT_ID_TAG);
        villager.setPersistenceRequired();
    }

    private static Villager findLoadedVillager(MinecraftServer server, UUID villagerId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(villagerId);
            if (entity instanceof Villager villager) {
                return villager;
            }
        }
        return null;
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

    private static void notifyLeader(MinecraftServer server, PartyRecord party, String messageKey) {
        ServerPlayer leader = server.getPlayerList().getPlayer(party.leaderId());
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable(messageKey));
        }
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
