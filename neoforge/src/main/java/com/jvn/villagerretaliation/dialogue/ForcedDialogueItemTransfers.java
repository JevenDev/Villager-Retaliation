package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemDestination;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.inventory.VillagerConfiscatedStolenItemTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class ForcedDialogueItemTransfers {
    private ForcedDialogueItemTransfers() {
    }

    static boolean executeItemPayment(
            ServerPlayer player,
            Villager villager,
            SourceContainer sourceContainer,
            ForcedDialogueItemPayment itemPayment) {
        List<ItemStack> previewStacks = itemPayment.removal().previewRemovedStacks(player);
        if (previewStacks.isEmpty()) {
            return false;
        }

        Optional<ItemTransferTarget> primaryTarget = transferTarget(player, villager, sourceContainer, itemPayment.destination());
        if (primaryTarget.isEmpty()) {
            return false;
        }

        Optional<ItemTransferTarget> overflowTarget = Optional.ofNullable(itemPayment.overflowDestination())
                .flatMap(destination -> transferTarget(player, villager, sourceContainer, destination));
        boolean primaryFits = primaryTarget.get().canAccept(previewStacks);
        if (itemPayment.requireSpace() && !primaryFits && overflowTarget.isEmpty()) {
            return false;
        }
        if (overflowTarget.isPresent() && !overflowTarget.get().canAccept(previewStacks)) {
            return false;
        }

        Optional<List<ItemStack>> removedStacks = itemPayment.removal().removeStacks(player);
        if (removedStacks.isEmpty()) {
            return false;
        }

        List<ItemStack> remainder = primaryTarget.get().accept(removedStacks.get());
        if (!remainder.isEmpty() && overflowTarget.isPresent()) {
            remainder = overflowTarget.get().accept(remainder);
        }
        return !itemPayment.requireSpace() || remainder.isEmpty();
    }

    static Optional<List<ItemStack>> executeStolenItemReturn(
            ServerPlayer player,
            Villager villager,
            SourceContainer sourceContainer,
            List<ItemStack> removedStacks,
            ForcedDialogueStolenItemReturn stolenItemReturn) {
        if (removedStacks.isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemTransferTarget> target = transferTarget(player, villager, sourceContainer, stolenItemReturn.destination(), true);
        if (target.isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemTransferTarget> overflowTarget = Optional.ofNullable(stolenItemReturn.overflowDestination())
                .flatMap(destination -> transferTarget(player, villager, sourceContainer, destination, true));
        boolean targetFits = target.get().canAccept(removedStacks);
        if (stolenItemReturn.requireSpace() && !targetFits && overflowTarget.isEmpty()) {
            return Optional.empty();
        }
        if (overflowTarget.isPresent() && !overflowTarget.get().canAccept(removedStacks)) {
            return Optional.empty();
        }

        Optional<List<ItemStack>> removedFromPlayer = removeSpecificStacks(player, removedStacks);
        if (removedFromPlayer.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> remainder = target.get().accept(removedFromPlayer.get());
        if (!remainder.isEmpty() && overflowTarget.isPresent()) {
            remainder = overflowTarget.get().accept(remainder);
        }
        return !stolenItemReturn.requireSpace() || remainder.isEmpty()
                ? Optional.of(removedFromPlayer.get())
                : Optional.empty();
    }

    private static Optional<List<ItemStack>> removeSpecificStacks(ServerPlayer player, List<ItemStack> targets) {
        if (!canRemoveSpecificStacks(player, targets)) {
            return Optional.empty();
        }

        List<ItemStack> removedStacks = new ArrayList<>();
        for (ItemStack target : targets) {
            int remaining = target.getCount();
            for (ItemStack stack : removablePlayerStacks(player)) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) {
                    continue;
                }

                int removed = Math.min(remaining, stack.getCount());
                removedStacks.add(stack.copyWithCount(removed));
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
        return Optional.of(List.copyOf(removedStacks));
    }

    private static boolean canRemoveSpecificStacks(ServerPlayer player, List<ItemStack> targets) {
        List<ItemStack> availableStacks = removablePlayerStacks(player).stream()
                .map(ItemStack::copy)
                .toList();
        for (ItemStack target : targets) {
            int remaining = target.getCount();
            for (ItemStack stack : availableStacks) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) {
                    continue;
                }

                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> removablePlayerStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static Optional<ItemTransferTarget> transferTarget(
            ServerPlayer player,
            Villager villager,
            SourceContainer sourceContainer,
            ForcedDialogueItemDestination destination) {
        return transferTarget(player, villager, sourceContainer, destination, false);
    }

    private static Optional<ItemTransferTarget> transferTarget(
            ServerPlayer player,
            Villager villager,
            SourceContainer sourceContainer,
            ForcedDialogueItemDestination destination,
            boolean markConfiscatedStolenItems) {
        return switch (destination) {
            case DISCARD -> Optional.of(discardTransferTarget());
            case VILLAGER_INVENTORY -> Optional.of(villagerInventoryTransferTarget(player, villager, markConfiscatedStolenItems));
            case VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER -> sourceContainer(player, sourceContainer)
                    .map(container -> chainedTransferTarget(
                            villagerInventoryTransferTarget(player, villager, markConfiscatedStolenItems),
                            containerTransferTarget(container)));
            case SOURCE_CONTAINER -> sourceContainer(player, sourceContainer).map(ForcedDialogueItemTransfers::containerTransferTarget);
            case DROP_AT_VILLAGER -> Optional.of(dropAtVillagerTransferTarget(villager));
            case DROP_AT_CONTAINER -> sourceContainerLevel(player, sourceContainer)
                    .map(level -> dropAtContainerTransferTarget(level, sourceContainer.pos()));
        };
    }

    private static Optional<Container> sourceContainer(ServerPlayer player, SourceContainer sourceContainer) {
        Optional<ServerLevel> level = sourceContainerLevel(player, sourceContainer);
        if (level.isEmpty()) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.get().getBlockEntity(sourceContainer.pos());
        return blockEntity instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    private static Optional<ServerLevel> sourceContainerLevel(ServerPlayer player, SourceContainer sourceContainer) {
        ServerLevel level = player.getServer().getLevel(sourceContainer.dimension());
        return Optional.ofNullable(level);
    }

    private static ItemTransferTarget discardTransferTarget() {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                return List.of();
            }
        };
    }

    private static ItemTransferTarget villagerInventoryTransferTarget(
            ServerPlayer player,
            Villager villager,
            boolean markConfiscatedStolenItems) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return VillagerInventoryAccess.canAddItems(villager, villagerInventoryStacks(stacks));
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                return stacks.stream()
                        .map(this::acceptOne)
                        .filter(stack -> !stack.isEmpty())
                        .toList();
            }

            private ItemStack acceptOne(ItemStack stack) {
                ItemStack transferredStack = villagerInventoryStack(stack);
                ItemStack remainder = VillagerInventoryAccess.addItem(villager, transferredStack);
                int acceptedCount = stack.getCount() - remainder.getCount();
                if (markConfiscatedStolenItems && acceptedCount > 0) {
                    VillagerConfiscatedStolenItemTracker.recordConfiscatedStolenItem(
                            player.serverLevel(),
                            villager,
                            player,
                            stack,
                            acceptedCount);
                }
                return markConfiscatedStolenItems && !remainder.isEmpty()
                        ? stack.copyWithCount(remainder.getCount())
                        : remainder;
            }

            private List<ItemStack> villagerInventoryStacks(List<ItemStack> stacks) {
                if (!markConfiscatedStolenItems) {
                    return stacks;
                }
                return stacks.stream().map(this::villagerInventoryStack).toList();
            }

            private ItemStack villagerInventoryStack(ItemStack stack) {
                return markConfiscatedStolenItems
                        ? VillagerConfiscatedStolenItemTracker.markConfiscatedStolenItem(stack.copy(), player, villager)
                        : stack;
            }
        };
    }

    private static ItemTransferTarget containerTransferTarget(Container container) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return canInsertAll(container, stacks);
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                return insertAll(container, stacks);
            }
        };
    }

    private static ItemTransferTarget chainedTransferTarget(ItemTransferTarget primary, ItemTransferTarget fallback) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return primary.canAccept(stacks) || fallback.canAccept(stacks);
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                List<ItemStack> remainder = primary.accept(stacks);
                return remainder.isEmpty() ? List.of() : fallback.accept(remainder);
            }
        };
    }

    private static ItemTransferTarget dropAtVillagerTransferTarget(Villager villager) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                for (ItemStack stack : stacks) {
                    villager.spawnAtLocation(stack.copy());
                }
                return List.of();
            }
        };
    }

    private static ItemTransferTarget dropAtContainerTransferTarget(ServerLevel level, BlockPos pos) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                for (ItemStack stack : stacks) {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, stack.copy());
                }
                return List.of();
            }
        };
    }

    private static boolean canInsertAll(Container container, List<ItemStack> stacks) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            slots.add(container.getItem(slot).copy());
        }
        for (ItemStack stack : stacks) {
            ItemStack remainder = insertIntoSlots(simulatedSlots(container, slots), stack);
            if (!remainder.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> insertAll(Container container, List<ItemStack> stacks) {
        List<ItemStack> remainders = new ArrayList<>();
        ItemSlots slots = containerSlots(container);
        for (ItemStack stack : stacks) {
            ItemStack remainder = insertIntoSlots(slots, stack);
            if (!remainder.isEmpty()) {
                remainders.add(remainder);
            }
        }
        container.setChanged();
        return List.copyOf(remainders);
    }

    private static ItemStack insertIntoSlots(ItemSlots slots, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < slots.size(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = slots.get(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !slots.canPlace(slot, remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), slots.maxStackSize(slot, remainder));
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                existing.grow(moveCount);
                remainder.shrink(moveCount);
            }
        }

        for (int slot = 0; slot < slots.size(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!slots.get(slot).isEmpty() || !slots.canPlace(slot, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), slots.maxStackSize(slot, remainder)));
            slots.set(slot, remainder.copyWithCount(moveCount));
            remainder.shrink(moveCount);
        }
        return remainder;
    }

    private static ItemSlots containerSlots(Container container) {
        return new ItemSlots() {
            @Override
            public int size() {
                return container.getContainerSize();
            }

            @Override
            public ItemStack get(int slot) {
                return container.getItem(slot);
            }

            @Override
            public void set(int slot, ItemStack stack) {
                container.setItem(slot, stack);
            }

            @Override
            public boolean canPlace(int slot, ItemStack stack) {
                return container.canPlaceItem(slot, stack);
            }

            @Override
            public int maxStackSize(int slot, ItemStack stack) {
                return container.getMaxStackSize();
            }
        };
    }

    private static ItemSlots simulatedSlots(Container container, List<ItemStack> slots) {
        return new ItemSlots() {
            @Override
            public int size() {
                return slots.size();
            }

            @Override
            public ItemStack get(int slot) {
                return slots.get(slot);
            }

            @Override
            public void set(int slot, ItemStack stack) {
                slots.set(slot, stack);
            }

            @Override
            public boolean canPlace(int slot, ItemStack stack) {
                return container.canPlaceItem(slot, stack);
            }

            @Override
            public int maxStackSize(int slot, ItemStack stack) {
                return container.getMaxStackSize();
            }
        };
    }

    record SourceContainer(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private interface ItemTransferTarget {
        boolean canAccept(List<ItemStack> stacks);

        List<ItemStack> accept(List<ItemStack> stacks);
    }

    private interface ItemSlots {
        int size();

        ItemStack get(int slot);

        void set(int slot, ItemStack stack);

        boolean canPlace(int slot, ItemStack stack);

        int maxStackSize(int slot, ItemStack stack);
    }
}
