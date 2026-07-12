package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
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
                level.dimension().location(),
                villager.blockPosition()
        );
        if (currentParty != null) {
            record.setAttackWithParty(currentParty.attackWithParty());
            record.setDefendParty(currentParty.defendParty());
        }

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

        VillagerWalletService.addCurrency(villager, DAILY_EMERALD_COST, VillagerWalletService.WalletSource.HIRE_PAYMENT);
        attachEntityState(level, villager, membership.partyId(), record);
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contractId);
        VillagerRecruitmentService.applyPartyFollowing(level, villager, player);
        PartySyncService.syncParty(level.getServer(), membership.partyId());
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
        if (!isAuthorizedPartyMember(player, party, record)) {
            return ContractResult.failure("villagerretaliation.party.error.not_in_party");
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
        VillagerWalletService.addCurrency(villager, cost, VillagerWalletService.WalletSource.HIRE_PAYMENT);
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
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        context.record().setFollowing();
        PartyService.markChanged(context.level());
        VillagerRecruitmentService.applyPartyFollowing(context.level(), villager, player);
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
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
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
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
        PartySyncService.syncParty(context.level().getServer(), context.party().id());
        return ContractResult.success("villagerretaliation.party.villager_dismissed", context.party().id(), removed, 0, 0);
    }

    public static ContractResult toggleAttackWithParty(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        context.record().setAttackWithParty(!context.record().attackWithParty());
        return settingsChanged(context, "villagerretaliation.party.villager_settings_updated");
    }

    public static ContractResult toggleDefendParty(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
        }
        context.record().setDefendParty(!context.record().defendParty());
        return settingsChanged(context, "villagerretaliation.party.villager_settings_updated");
    }

    public static ContractResult cycleDropCollectionMode(ServerPlayer player, Villager villager) {
        PartyVillagerContext context = authorizedContext(player, villager);
        if (context == null) {
            return ContractResult.failure("villagerretaliation.party.error.leader_only");
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
                level.dimension().location(),
                villager.blockPosition());
        PartyService.markChanged(level);
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
                            updateLastKnownLocation(record, villager);
                            PartyService.markChanged(level);
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
        return isAuthorizedPartyMember(player, party, record)
                && (party.leaderId().equals(player.getUUID()) || party.sharedVillagerInventories());
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
            updateLastKnownLocation(record, loaded);
            cleanupEntity(loaded);
            closeJobInventories(server, loaded.getId());
        }
        notifyLeader(server, party, "villagerretaliation.party.contract_expired", record);
        PartySyncService.syncParty(server, party.id());
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

    private static void updateLastKnownLocation(PartyVillagerRecord record, Villager villager) {
        record.updateDisplay(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                professionTranslationKey(villager),
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
