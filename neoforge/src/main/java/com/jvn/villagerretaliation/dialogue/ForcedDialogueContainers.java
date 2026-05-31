package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.config.ContainerWatchMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class ForcedDialogueContainers {
    private ForcedDialogueContainers() {
    }

    static boolean isEligibleWatchedContainer(BlockState state, ResourceLocation lootTable) {
        if (!isWatchedContainer(state) && lootTable == null) {
            return false;
        }
        return lootTable != null
                || VillagerRetaliationConfig.CONTAINER_WATCH_MODE.get() == ContainerWatchMode.ALL_WATCHED_CONTAINERS;
    }

    static ResourceLocation generatedLootTable(ServerLevel level, BlockPos pos) {
        return GeneratedContainerSavedData.generatedContainerLootTable(level, pos).orElse(null);
    }

    static void unpackLootTable(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainer container) {
            container.unpackLootTable(player);
        }
    }

    static int breakReputationDelta(ContainerSnapshot snapshot) {
        int delta = VillagerRetaliationConfig.CONTAINER_BREAK_REPUTATION_LOSS.get();
        if (snapshot.lootTable() != null && snapshot.itemCount() > 0) {
            delta += snapshot.itemCount() * VillagerRetaliationConfig.GENERATED_CONTAINER_BREAK_ITEM_REPUTATION_LOSS.get();
        }
        return delta;
    }

    static int countItems(AbstractContainerMenu menu) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static List<ItemStack> snapshotItems(AbstractContainerMenu menu) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    static int countItems(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static List<ItemStack> snapshotItems(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    static ItemStack restoreToOpenMenu(AbstractContainerMenu menu, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (Slot slot : menu.slots) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !slot.mayPlace(remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), slot.getMaxStackSize(remainder));
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                existing.grow(moveCount);
                remainder.shrink(moveCount);
                slot.setChanged();
            }
        }

        for (Slot slot : menu.slots) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot.container instanceof Inventory || !slot.getItem().isEmpty() || !slot.mayPlace(remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), slot.getMaxStackSize(remainder)));
            slot.set(remainder.copyWithCount(moveCount));
            slot.setChanged();
            remainder.shrink(moveCount);
        }
        return remainder;
    }

    static List<ItemStack> removedStacks(List<ItemStack> beforeStacks, AbstractContainerMenu menu) {
        List<ItemStack> remainingCurrent = new ArrayList<>(snapshotItems(menu));
        List<ItemStack> removed = new ArrayList<>();
        for (ItemStack beforeStack : beforeStacks) {
            int missingCount = beforeStack.getCount();
            for (ItemStack currentStack : remainingCurrent) {
                if (missingCount <= 0) {
                    break;
                }
                if (currentStack.isEmpty() || !ItemStack.isSameItemSameComponents(beforeStack, currentStack)) {
                    continue;
                }

                int matched = Math.min(missingCount, currentStack.getCount());
                currentStack.shrink(matched);
                missingCount -= matched;
            }
            if (missingCount > 0) {
                removed.add(beforeStack.copyWithCount(missingCount));
            }
        }
        return List.copyOf(removed);
    }

    static ItemStack representativeStack(List<ItemStack> removedStacks) {
        return removedStacks.stream()
                .max(java.util.Comparator.comparingInt(ItemStack::getCount))
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
    }

    static String stackName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        return stack.getCount() > 1 ? stack.getCount() + "x " + name : name;
    }

    static String stackListName(List<ItemStack> stacks) {
        return stacks.stream()
                .map(ForcedDialogueContainers::stackName)
                .reduce((left, right) -> left + ", " + right)
                .orElse("items");
    }

    static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        return stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    private static boolean isWatchedContainer(BlockState state) {
        return state.getBlock() instanceof AbstractChestBlock<?>
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }
}

record RecentContainerClick(
        ResourceKey<Level> dimension,
        BlockPos pos,
        long gameTime,
        Component containerName,
        ResourceLocation lootTable) {
    RecentContainerClick {
        pos = pos.immutable();
    }
}

record ContainerSnapshot(
        ResourceKey<Level> dimension,
        BlockPos pos,
        Component containerName,
        ResourceLocation lootTable,
        int itemCount,
        List<ItemStack> itemStacks,
        long gameTime) {
    ContainerSnapshot {
        pos = pos.immutable();
        itemStacks = ForcedDialogueContainers.copyStacks(itemStacks);
    }
}
