package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignmentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AssignedStorageService {
    private AssignedStorageService() {
    }

    public static boolean hasAssignedStorage(ServerLevel level, Villager villager) {
        return !AssignedStorageSavedData.get(level).assignedTo(villager.getUUID()).isEmpty();
    }

    public static List<AssignedContainerRecord> assignedStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).assignedTo(villager.getUUID());
    }

    public static AssignSummary assign(
            ServerPlayer player,
            Villager villager,
            List<StoragePosition> positions,
            String purpose) {
        if (positions.isEmpty()) {
            return new AssignSummary(0, 0, 0);
        }
        AssignedStorageSavedData data = AssignedStorageSavedData.get(player.serverLevel());
        int assigned = 0;
        int alreadyAssigned = 0;
        int invalid = 0;
        int priorityBase = data.assignedTo(villager.getUUID()).size();
        for (StoragePosition position : positions) {
            ServerLevel targetLevel = player.server.getLevel(position.dimension());
            if (targetLevel == null || !isValidContainer(targetLevel, position.pos())) {
                invalid++;
                continue;
            }
            AssignmentResult result = data.assign(new AssignedContainerRecord(
                    position.dimension(),
                    position.pos().immutable(),
                    villager.getUUID(),
                    player.getUUID(),
                    purpose == null || purpose.isBlank() ? "general" : purpose,
                    priorityBase + assigned,
                    "valid"
            ));
            if (result == AssignmentResult.ASSIGNED) {
                assigned++;
            } else {
                alreadyAssigned++;
            }
        }
        return new AssignSummary(assigned, alreadyAssigned, invalid);
    }

    public static int removeAssignedStorage(ServerLevel level, Villager villager) {
        return AssignedStorageSavedData.get(level).removeAssignedTo(villager.getUUID());
    }

    public static boolean isValidContainer(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container;
    }

    public static ItemStack depositStack(Villager villager, ItemStack stack) {
        if (stack.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
            return stack;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = liveContainerCandidates(level, villager);
        if (containers.isEmpty()) {
            return stack;
        }
        List<VillagerInventoryOverflowService.ContainerCandidate> usedContainers = new ArrayList<>();
        ItemStack remainder = VillagerInventoryOverflowService.insertIntoContainers(containers, stack, usedContainers);
        VillagerInventoryOverflowService.openUsedContainers(level, usedContainers);
        return remainder;
    }

    static List<VillagerInventoryOverflowService.ContainerCandidate> liveContainerCandidates(ServerLevel level, Villager villager) {
        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        List<VillagerInventoryOverflowService.ContainerCandidate> containers = new ArrayList<>();
        for (AssignedContainerRecord record : data.assignedTo(villager.getUUID())) {
            ServerLevel targetLevel = level.getServer().getLevel(record.dimension());
            if (targetLevel == null) {
                data.updateValidation(record, "missing_dimension");
                continue;
            }
            if (targetLevel != level) {
                data.updateValidation(record, "wrong_dimension");
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(record.pos());
            if (!(blockEntity instanceof Container container)) {
                data.updateValidation(record, "missing_container");
                continue;
            }
            data.updateValidation(record, "valid");
            containers.add(new VillagerInventoryOverflowService.ContainerCandidate(record.pos().immutable(), container));
        }
        return containers;
    }

    public static Component assignmentSummary(AssignSummary summary) {
        if (summary.assigned() > 0) {
            return Component.literal("Assigned " + summary.assigned() + " container" + (summary.assigned() == 1 ? "" : "s") + ".");
        }
        if (summary.alreadyAssigned() > 0) {
            return Component.literal("That storage is already assigned to another villager.");
        }
        if (summary.invalid() > 0) {
            return Component.literal("No valid selected containers were found.");
        }
        return Component.literal("No storage was assigned.");
    }

    public record StoragePosition(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record AssignSummary(int assigned, int alreadyAssigned, int invalid) {
    }
}
