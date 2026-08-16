package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import com.jvn.villagerretaliation.quest.VillagerQuestHiringRestrictionService;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Handles paid hiring and extension actions from the recruitment interaction. */
public final class HiredContractRequestHandler {
    private HiredContractRequestHandler() {
    }

    public static boolean handle(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action,
            HiredVillagerRole selectedRole) {
        int hireDays = RecruitmentActionMappings.hireDays(action);
        if (hireDays > 0) {
            handleHire(player, level, villager, hireDays, selectedRole);
            return true;
        }
        int extensionDays = RecruitmentActionMappings.extensionDays(action);
        if (extensionDays > 0) {
            handleExtension(player, level, villager, extensionDays);
            return true;
        }
        return false;
    }

    private static void handleHire(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            int days,
            HiredVillagerRole selectedRole) {
        if (HiredVillagerContractService.isHiredBy(level, villager, player)) {
            VillagerInteractionService.sendHiredContractNotice(player, level, villager);
            return;
        }
        if (com.jvn.villagerretaliation.party.PartyService.getPartyForVillager(level, villager.getUUID()).isPresent()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.party.error.already_in_party");
            return;
        }
        if (HiredVillagerContractService.isHired(level, villager)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.hired_contract_taken");
            return;
        }
        if (VillagerQuestHiringRestrictionService.blocksHiring(level, villager, player)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.hire.quest_provider_locked");
            return;
        }
        if (villager.isTrading() || villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.recruit_unavailable");
            return;
        }
        HiredVillagerIndex.reconcileLoadedFor(player);
        if (HiredVillagerIndex.targetsFor(player).size() >= HiredVillagerIndex.MAX_ASSIGNMENTS_PER_PLAYER) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.recruit_unavailable");
            return;
        }
        if (HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, player)) {
            VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.hire_overflow_blocked",
                    HiredVillagerContractService.jobInventoryOverflowReplacements(level, villager));
            return;
        }

        HiredVillagerRole hireRole = selectedRole == null
                ? HiredVillagerRoles.defaultRole(level, villager)
                : selectedRole;
        if (!HiredVillagerRoles.availableContractRoles(level, villager).contains(hireRole)) {
            VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.role_not_suitable",
                    Map.of("role", hireRole.label()));
            return;
        }

        int cost = HiredVillagerContractService.getHireCost(level, villager, player, days, hireRole);
        TransactionResult transaction = transact(
                player,
                cost,
                () -> HiredVillagerContractService.startHireContract(
                        level, villager, player, days, cost, hireRole));
        if (transaction == TransactionResult.PAYMENT_FAILED) {
            VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.hire_cost",
                    Map.of(
                            "time_remaining", VillagerInteractionService.formatDaysRemaining(days),
                            "contract_cost", VillagerInteractionService.formatCurrency(level, cost)));
            return;
        }
        if (transaction == TransactionResult.MUTATION_FAILED) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.recruit_unavailable");
            return;
        }

        HiredVillagerWorkService.resetForNewContract(level, villager);
        VillagerRecruitmentService.sendHiredNotice(player, villager);
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                "interaction.hire_started",
                Map.of(
                        "time_remaining", VillagerInteractionService.formatDaysRemaining(days),
                        "contract_cost", VillagerInteractionService.formatCurrency(level, cost),
                        "role", hireRole.label()));
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    private static void handleExtension(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            int days) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.hire.extend_requires_hirer");
            return;
        }
        int extensionDays = HiredVillagerContractService.getAvailableExtensionDays(level, villager, player, days);
        if (extensionDays <= 0) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.extend_unavailable");
            return;
        }
        int cost = HiredVillagerContractService.getExtensionCost(level, villager, player, days);
        TransactionResult transaction = transact(
                player,
                cost,
                () -> HiredVillagerContractService.extendHireContract(level, villager, player, days, cost));
        if (transaction == TransactionResult.PAYMENT_FAILED) {
            VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.extend_cost",
                    Map.of(
                            "time_remaining", VillagerInteractionService.formatDaysRemaining(extensionDays),
                            "contract_cost", VillagerInteractionService.formatCurrency(level, cost)));
            return;
        }
        if (transaction == TransactionResult.MUTATION_FAILED) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.extend_unavailable");
            return;
        }

        int remainingDays = HiredVillagerContractService.getRemainingHireDays(level, villager);
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                "interaction.extend_success",
                Map.of(
                        "time_remaining", VillagerInteractionService.formatDaysRemaining(extensionDays),
                        "contract_cost", VillagerInteractionService.formatCurrency(level, cost),
                        "new_time_remaining", VillagerInteractionService.formatDaysRemaining(remainingDays)));
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
    }

    static TransactionResult transact(ServerPlayer player, int cost, BooleanSupplier mutation) {
        if (!VillagerCurrencyPayment.tryRemove(player, cost)) {
            return TransactionResult.PAYMENT_FAILED;
        }
        if (mutation.getAsBoolean()) {
            return TransactionResult.SUCCESS;
        }
        VillagerCurrencyPayment.give(player, cost);
        return TransactionResult.MUTATION_FAILED;
    }

    enum TransactionResult {
        SUCCESS,
        PAYMENT_FAILED,
        MUTATION_FAILED
    }
}
