package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared material-deficit and assigned-storage acquisition logic for production jobs. */
public final class HiredProductionMaterials {
    private HiredProductionMaterials() {
    }

    public static List<Need> missingItemNeeds(HiredWorkContext context, Map<Item, Integer> requiredItems) {
        List<Need> needs = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            Item item = entry.getKey();
            int missing = Math.max(0, entry.getValue() - HiredSupplyCrafting.countCarried(context, item));
            if (missing > 0) {
                needs.add(new Need(stack -> stack.is(item), missing, new ItemStack(item).getHoverName().getString()));
            }
        }
        return needs;
    }

    public static Acquisition acquireFromAssignedStorage(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<Need> needs,
            double speed,
            int baseTransferLimit,
            StorageFilterPolicy filterPolicy) {
        if (needs.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            return new Acquisition(Status.NO_NEEDS, null, 0);
        }
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            return new Acquisition(Status.MISSING, null, 0);
        }

        Predicate<ItemStack> requested = stack -> matchesAny(needs, stack);
        BlockPos storage = nearestStorage(level, villager, requested, filterPolicy);
        if (storage == null) {
            return new Acquisition(Status.MISSING, null, 0);
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result movement =
                HiredStorageNavigationGoal.moveToStorageTarget(level, context, villager, storage, speed);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            return new Acquisition(Status.MOVING, storage, 0);
        }
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            BlockPos failedStorage = storage;
            for (BlockPos alternate : matchingStorages(level, villager, requested, filterPolicy)) {
                if (failedStorage.equals(alternate)) {
                    continue;
                }
                HiredWorkerBrain.setStorageTarget(context, alternate);
                HiredStorageNavigationGoal.Result alternateMovement =
                        HiredStorageNavigationGoal.moveToStorageTarget(level, context, villager, alternate, speed);
                if (alternateMovement == HiredStorageNavigationGoal.Result.MOVING) {
                    return new Acquisition(Status.MOVING, alternate, 0);
                }
                if (alternateMovement == HiredStorageNavigationGoal.Result.ARRIVED) {
                    storage = alternate;
                    movement = alternateMovement;
                    break;
                }
            }
            if (movement == HiredStorageNavigationGoal.Result.FAILED) {
                HiredWorkerBrain.setStorageTarget(context, failedStorage);
                return new Acquisition(Status.UNREACHABLE, failedStorage, 0);
            }
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        int remaining = context.transferLimit(baseTransferLimit);
        int movedTotal = 0;
        for (Need need : needs) {
            if (remaining <= 0) {
                break;
            }
            int moved = transfer(villager, storage, need, remaining, context, filterPolicy);
            movedTotal += moved;
            remaining -= moved;
        }
        if (movedTotal <= 0) {
            return new Acquisition(Status.INVENTORY_FULL, storage, 0);
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        return new Acquisition(Status.COLLECTED, storage, movedTotal);
    }

    private static boolean matchesAny(List<Need> needs, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Need need : needs) {
            if (need.predicate().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos nearestStorage(
            ServerLevel level, Villager villager, Predicate<ItemStack> predicate, StorageFilterPolicy policy) {
        return policy == StorageFilterPolicy.RESPECT_INPUT_FILTER
                ? AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, predicate)
                : AssignedStorageService.nearestAssignedStoragePosContainingIgnoringFilter(level, villager, predicate);
    }

    private static List<BlockPos> matchingStorages(
            ServerLevel level, Villager villager, Predicate<ItemStack> predicate, StorageFilterPolicy policy) {
        return policy == StorageFilterPolicy.RESPECT_INPUT_FILTER
                ? AssignedStorageService.assignedStoragePositionsContaining(level, villager, predicate)
                : AssignedStorageService.assignedStoragePositionsContainingIgnoringFilter(level, villager, predicate);
    }

    private static int transfer(
            Villager villager,
            BlockPos storage,
            Need need,
            int remaining,
            HiredWorkContext context,
            StorageFilterPolicy policy) {
        int amount = Math.min(need.count(), remaining);
        return policy == StorageFilterPolicy.RESPECT_INPUT_FILTER
                ? AssignedStorageService.transferItemsAtAssignedStorage(
                        villager, storage, need.predicate(), amount, context.inventory()::insertSupplyFromStorage)
                : AssignedStorageService.transferItemsAtAssignedStorageIgnoringFilter(
                        villager, storage, need.predicate(), amount, context.inventory()::insertSupplyFromStorage);
    }

    public record Need(Predicate<ItemStack> predicate, int count, String label) {
    }

    public record Acquisition(Status status, BlockPos storagePos, int moved) {
    }

    public enum Status {
        NO_NEEDS,
        MISSING,
        MOVING,
        UNREACHABLE,
        INVENTORY_FULL,
        COLLECTED
    }

    public enum StorageFilterPolicy {
        RESPECT_INPUT_FILTER,
        IGNORE_INPUT_FILTER
    }
}
