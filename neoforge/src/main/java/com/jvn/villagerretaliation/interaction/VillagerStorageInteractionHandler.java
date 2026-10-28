package com.jvn.villagerretaliation.interaction;

import static com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice;
import static com.jvn.villagerretaliation.interaction.VillagerInteractionService.formatCurrency;
import static com.jvn.villagerretaliation.interaction.VillagerInteractionService.canManageAssignedStorage;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.network.RecruitmentResultPayload.FailureReason;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

final class VillagerStorageInteractionHandler {
    private VillagerStorageInteractionHandler() { }

    static FailureReason showAssignedStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.storage.inspect_requires_access");
            return FailureReason.NOT_OWNER;
        }
        List<AssignedContainerRecord> assigned = AssignedStorageService.assignedStorage(level, villager);
        HiredStorageClipboardItem.sendAssignedStorageOutlines(player, assigned);
        int count = assigned.size();
        sendVillagerNotice(player, villager, "interaction.storage.assigned_count", Map.of("count", Integer.toString(count)));
        return FailureReason.NONE;
    }

    static FailureReason showAssignedPaymentStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.payment_storage.requires_hire");
            return FailureReason.NOT_OWNER;
        }
        List<AssignedContainerRecord> assigned = AssignedStorageService.assignedPaymentStorage(level, villager);
        HiredStorageClipboardItem.sendAssignedStorageOutlines(player, assigned);
        int count = assigned.size();
        sendVillagerNotice(player, villager, "interaction.payment_storage.assigned_count", Map.of(
                "count", Integer.toString(count),
                "auto_payment", HiredVillagerContractService.isAutoPaymentEnabled(level, villager) ? "on" : "off"));
        return FailureReason.NONE;
    }

    static FailureReason depositEarnings(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.earnings.deposit_requires_access");
            return FailureReason.NOT_OWNER;
        }
        int excess = VillagerWalletService.getDepositAmount(villager);
        if (excess <= 0) {
            sendVillagerNotice(player, villager, "interaction.earnings.no_excess");
            return FailureReason.INVALID_TRANSITION;
        }
        VillagerWalletService.DepositResult result = VillagerWalletService.tryDepositExcessCurrency(villager);
        if (result.storageUnavailable()) {
            sendVillagerNotice(player, villager, "interaction.earnings.storage_unavailable");
        } else if (!result.assignedStorageAvailable()) {
            sendVillagerNotice(player, villager, "interaction.earnings.no_assigned_storage");
        } else if (result.deposited() <= 0) {
            sendVillagerNotice(player, villager, "interaction.earnings.storage_full");
        } else if (result.remaining() > 0) {
            sendVillagerNotice(player, villager, "interaction.earnings.deposited_partial", Map.of(
                    "deposited", formatCurrency(level, result.deposited()),
                    "remaining", formatCurrency(level, result.remaining())));
        } else {
            sendVillagerNotice(player, villager, "interaction.earnings.deposited_all", Map.of(
                    "deposited", formatCurrency(level, result.deposited())));
        }
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return result.deposited() > 0 ? FailureReason.NONE : FailureReason.INVALID_TRANSITION;
    }

    static FailureReason removeAssignedStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!canManageAssignedStorage(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.storage.remove_requires_access");
            return FailureReason.NOT_OWNER;
        }
        int removed = AssignedStorageService.removeAssignedStorage(level, villager);
        sendVillagerNotice(player, villager, "interaction.storage.removed_count", Map.of(
                "count", Integer.toString(removed),
                "plural", (removed == 1 ? "" : "s")));
        return FailureReason.NONE;
    }

    static FailureReason removeAssignedPaymentStorage(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.payment_storage.remove_requires_hirer");
            return FailureReason.NOT_OWNER;
        }
        int removed = AssignedStorageService.removeAssignedPaymentStorage(level, villager);
        if (removed > 0) {
            HiredVillagerContractService.setAutoPaymentEnabled(villager, false);
        }
        sendVillagerNotice(player, villager, "interaction.payment_storage.removed_count", Map.of(
                "count", Integer.toString(removed),
                "plural", (removed == 1 ? "" : "s")));
        return FailureReason.NONE;
    }

    static FailureReason toggleAutoPayment(ServerPlayer player, ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.auto_payment.requires_hire");
            return FailureReason.NOT_OWNER;
        }
        if (!AssignedStorageService.hasAssignedPaymentStorage(level, villager)) {
            sendVillagerNotice(player, villager, "interaction.auto_payment.requires_storage");
            return FailureReason.INVALID_TRANSITION;
        }
        boolean enabled = HiredVillagerContractService.toggleAutoPayment(level, villager);
        sendVillagerNotice(player, villager, enabled
                ? "interaction.auto_payment.enabled"
                : "interaction.auto_payment.disabled");
        return FailureReason.NONE;
    }
}
