package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.dialogue.GeneratedContainerSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class VillagerInventoryOverflowService {
    private static final int SCAN_INTERVAL_TICKS = 100;
    private static final int SCAN_RADIUS = 12;
    private static final int CONTAINER_CLOSE_DELAY_TICKS = 20;
    private static final String VILLAGE_CHEST_PATH_PREFIX = "chests/village/";
    private static final String OWNER_ITEM_TAG = "VillagerRetaliationOwner";
    private static final String OWNER_UUID_TAG = "Villager";
    private static final String OWNER_NAME_TAG = "VillagerName";
    private static final Map<ContainerFeedbackKey, Long> PENDING_CONTAINER_CLOSES = new HashMap<>();

    private VillagerInventoryOverflowService() {
    }

    static void maybeOffloadInventoryOverflow(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        closePendingContainers(level);
        if (villager.isBaby()
                || VillagerInventoryContainer.hasOpenInventory(villager)
                || level.getGameTime() % SCAN_INTERVAL_TICKS != spreadTickOffset(villager)) {
            return;
        }

        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        if (!hasNoEmptySlots(inventory)) {
            return;
        }

        List<ContainerCandidate> containers = nearbyGeneratedVillageContainers(level, villager.blockPosition());
        if (containers.isEmpty()) {
            return;
        }

        boolean changed = false;
        List<ContainerCandidate> usedContainers = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack storedStack = inventory.get(slot);
            if (storedStack.isEmpty() || shouldKeepInInventory(storedStack)) {
                continue;
            }

            ItemStack offloadStack = markOwnedByVillager(storedStack.copy(), villager);
            ItemStack remainder = insertIntoContainers(containers, offloadStack, usedContainers);
            int movedCount = offloadStack.getCount() - remainder.getCount();
            if (movedCount <= 0) {
                continue;
            }

            storedStack.shrink(movedCount);
            if (storedStack.isEmpty()) {
                inventory.set(slot, ItemStack.EMPTY);
            }
            changed = true;
        }

        if (changed) {
            VillagerInventoryContainer.saveFullInventory(villager, inventory);
            openUsedContainers(level, usedContainers);
        }
    }

    private static long spreadTickOffset(Villager villager) {
        return Math.floorMod(villager.getUUID().getLeastSignificantBits(), SCAN_INTERVAL_TICKS);
    }

    private static boolean hasNoEmptySlots(NonNullList<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldKeepInInventory(ItemStack stack) {
        return VillagerGiftReturnTracker.giftedBy(stack).isPresent()
                || VillagerTradePaymentTracker.tradedBy(stack).isPresent()
                || VillagerConfiscatedStolenItemTracker.stolenItemBy(stack).isPresent();
    }

    private static List<ContainerCandidate> nearbyGeneratedVillageContainers(ServerLevel level, BlockPos center) {
        List<ContainerCandidate> containers = new ArrayList<>();
        BlockPos min = center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
        BlockPos max = center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof Container container)) {
                continue;
            }

            ResourceLocation lootTable = GeneratedContainerSavedData.generatedContainerLootTable(level, pos).orElse(null);
            if (!isVillageChestLootTable(lootTable)) {
                continue;
            }
            containers.add(new ContainerCandidate(pos.immutable(), container));
        }

        containers.sort(Comparator.comparingDouble(candidate -> candidate.pos().distSqr(center)));
        return containers;
    }

    private static boolean isVillageChestLootTable(ResourceLocation lootTable) {
        return lootTable != null
                && lootTable.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)
                && lootTable.getPath().startsWith(VILLAGE_CHEST_PATH_PREFIX);
    }

    private static ItemStack markOwnedByVillager(ItemStack stack, Villager villager) {
        if (stack.isEmpty() || hasOwnerMarker(stack)) {
            return stack;
        }

        Component villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(OWNER_UUID_TAG, villager.getUUID());
            ownerTag.putString(OWNER_NAME_TAG, villagerName.getString());
            tag.put(OWNER_ITEM_TAG, ownerTag);
        });
        Component ownerLine = Component.translatable("villagerretaliation.tooltip.belongs_to", villagerName)
                .withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(ownerLine));
        return stack;
    }

    private static boolean hasOwnerMarker(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return !customData.isEmpty() && customData.contains(OWNER_ITEM_TAG);
    }

    private static ItemStack insertIntoContainers(
            List<ContainerCandidate> containers,
            ItemStack stack,
            List<ContainerCandidate> usedContainers) {
        ItemStack remainder = stack.copy();
        for (ContainerCandidate candidate : containers) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int countBefore = remainder.getCount();
            remainder = insertIntoContainer(candidate.container(), remainder);
            if (remainder.getCount() < countBefore) {
                rememberUsedContainer(usedContainers, candidate);
            }
        }
        return remainder;
    }

    private static void rememberUsedContainer(List<ContainerCandidate> usedContainers, ContainerCandidate candidate) {
        for (ContainerCandidate usedContainer : usedContainers) {
            if (usedContainer.pos().equals(candidate.pos())) {
                return;
            }
        }
        usedContainers.add(candidate);
    }

    private static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                container.setChanged();
                return ItemStack.EMPTY;
            }

            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                existing.grow(moveCount);
                remainder.shrink(moveCount);
            }
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                container.setChanged();
                return ItemStack.EMPTY;
            }
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), container.getMaxStackSize()));
            container.setItem(slot, remainder.copyWithCount(moveCount));
            remainder.shrink(moveCount);
        }

        if (remainder.getCount() != stack.getCount()) {
            container.setChanged();
        }
        return remainder;
    }

    private static void openUsedContainers(ServerLevel level, List<ContainerCandidate> usedContainers) {
        for (ContainerCandidate candidate : usedContainers) {
            openContainerFeedback(level, candidate.pos());
        }
    }

    private static void openContainerFeedback(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity && ChestBlockEntity.getOpenCount(level, pos) <= 0) {
            level.blockEvent(pos, state.getBlock(), ChestBlock.EVENT_SET_OPEN_COUNT, 1);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
            scheduleContainerClose(level, pos);
        } else if (blockEntity instanceof BarrelBlockEntity
                && state.hasProperty(BarrelBlock.OPEN)
                && !state.getValue(BarrelBlock.OPEN)) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
            scheduleContainerClose(level, pos);
        } else if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox && shulkerBox.isClosed()) {
            level.blockEvent(pos, state.getBlock(), ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT, 1);
            level.playSound(null, pos, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
            scheduleContainerClose(level, pos);
        }
    }

    private static void scheduleContainerClose(ServerLevel level, BlockPos pos) {
        PENDING_CONTAINER_CLOSES.put(
                new ContainerFeedbackKey(level.dimension(), pos.immutable()),
                level.getGameTime() + CONTAINER_CLOSE_DELAY_TICKS
        );
    }

    private static void closePendingContainers(ServerLevel level) {
        long gameTime = level.getGameTime();
        Iterator<Map.Entry<ContainerFeedbackKey, Long>> iterator = PENDING_CONTAINER_CLOSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ContainerFeedbackKey, Long> entry = iterator.next();
            ContainerFeedbackKey key = entry.getKey();
            if (key.dimension() != level.dimension() || entry.getValue() > gameTime) {
                continue;
            }

            closeContainerFeedback(level, key.pos());
            iterator.remove();
        }
    }

    private static void closeContainerFeedback(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity) {
            level.blockEvent(pos, state.getBlock(), ChestBlock.EVENT_SET_OPEN_COUNT, 0);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        } else if (blockEntity instanceof BarrelBlockEntity && state.hasProperty(BarrelBlock.OPEN)) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
            level.blockEvent(pos, state.getBlock(), ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT, 0);
            level.playSound(null, pos, SoundEvents.SHULKER_BOX_CLOSE, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        }
    }

    private record ContainerCandidate(BlockPos pos, Container container) {
    }

    private record ContainerFeedbackKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
