package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.dialogue.forced.container.GeneratedContainerSavedData;
import com.jvn.villagerretaliation.dialogue.forced.container.GeneratedContainerLootResources;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.ChestType;

final class VillagerInventoryOverflowService {
    private static final int SCAN_INTERVAL_TICKS = 100;
    private static final int SCAN_RADIUS = 12;
    private static final int CONTAINER_CLOSE_DELAY_TICKS = 80;
    private static final String OWNER_ITEM_TAG = "VillagerRetaliationOwner";
    private static final String OWNER_UUID_TAG = "Villager";
    private static final String OWNER_NAME_TAG = "VillagerName";
    private static final Map<ContainerFeedbackKey, Long> PENDING_CONTAINER_CLOSES = new HashMap<>();

    private VillagerInventoryOverflowService() {
    }

    static void clearRuntimeState() {
        PENDING_CONTAINER_CLOSES.clear();
    }

    static void maybeOffloadInventoryOverflow(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        if (villager.isBaby()
                || VillagerInventoryContainer.hasOpenInventory(villager)
                || !TickThrottle.isSpreadTick(villager.getUUID(), level.getGameTime(), SCAN_INTERVAL_TICKS)) {
            return;
        }

        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        if (!hasNoEmptySlots(inventory)) {
            return;
        }

        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        List<ContainerCandidate> containers = hasAssignedStorage
                ? AssignedStorageService.liveContainerCandidates(level, villager)
                : nearbyGeneratedVillageContainers(level, villager.blockPosition());
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
                || VillagerConfiscatedStolenItemTracker.stolenItemBy(stack).isPresent()
                || ProtectedVillagerProperty.isProtected(stack);
    }

    private static List<ContainerCandidate> nearbyGeneratedVillageContainers(ServerLevel level, BlockPos center) {
        Map<BlockPos, ContainerCandidate> containers = new LinkedHashMap<>();
        int minChunkX = (center.getX() - SCAN_RADIUS) >> 4;
        int maxChunkX = (center.getX() + SCAN_RADIUS) >> 4;
        int minChunkZ = (center.getZ() - SCAN_RADIUS) >> 4;
        int maxChunkZ = (center.getZ() + SCAN_RADIUS) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity.isRemoved()
                            || !(blockEntity instanceof Container container)
                            || !isInsideScanCube(center, pos)) {
                        continue;
                    }

                    ResourceLocation lootTable = GeneratedContainerSavedData.generatedContainerLootTable(level, pos).orElse(null);
                    if (!GeneratedContainerLootResources.isVillagePropertyLootTable(level.getServer(), lootTable)) {
                        continue;
                    }
                    ContainerCandidate candidate = ContainerCandidate.resolve(level, pos.immutable(), container);
                    containers.putIfAbsent(candidate.pos(), candidate);
                }
            }
        }

        return containers.values().stream()
                .sorted(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(center)))
                .toList();
    }

    private static boolean isInsideScanCube(BlockPos center, BlockPos pos) {
        return Math.abs(pos.getX() - center.getX()) <= SCAN_RADIUS
                && Math.abs(pos.getY() - center.getY()) <= SCAN_RADIUS
                && Math.abs(pos.getZ() - center.getZ()) <= SCAN_RADIUS;
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

    static ItemStack insertIntoContainers(
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
            if (usedContainer.matches(candidate.pos())) {
                return;
            }
        }
        usedContainers.add(candidate);
    }

    static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                if (changed) {
                    container.setChanged();
                }
                return ItemStack.EMPTY;
            }

            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int requested = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (requested > 0) {
                int previousCount = existing.getCount();
                ItemStack updated = existing.copy();
                updated.grow(requested);
                container.setItem(slot, updated);
                ItemStack stored = container.getItem(slot);
                int moved = ItemStack.isSameItemSameComponents(stored, remainder)
                        ? Math.clamp(stored.getCount() - previousCount, 0, requested)
                        : 0;
                if (moved > 0) {
                    remainder.shrink(moved);
                    changed = true;
                }
            }
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                if (changed) {
                    container.setChanged();
                }
                return ItemStack.EMPTY;
            }
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int requested = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), container.getMaxStackSize()));
            container.setItem(slot, remainder.copyWithCount(requested));
            ItemStack stored = container.getItem(slot);
            int moved = ItemStack.isSameItemSameComponents(stored, remainder)
                    ? Math.clamp(stored.getCount(), 0, requested)
                    : 0;
            if (moved > 0) {
                remainder.shrink(moved);
                changed = true;
            }
        }

        if (changed) {
            container.setChanged();
        }
        return remainder;
    }

    /** Extracts only the count that observably left the source slot. */
    static ItemStack extractUpTo(Villager villager, Container container, int slot, int requested) {
        if (container == null || slot < 0 || slot >= container.getContainerSize() || requested <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack before = container.getItem(slot).copy();
        if (before.isEmpty()) {
            return ItemStack.EMPTY;
        }
        container.removeItem(slot, Math.min(requested, before.getCount()));
        ItemStack after = container.getItem(slot);
        int removedCount;
        if (after.isEmpty()) {
            removedCount = before.getCount();
        } else if (ItemStack.isSameItemSameComponents(before, after)) {
            removedCount = Math.max(0, before.getCount() - after.getCount());
        } else {
            removedCount = before.getCount();
        }
        if (removedCount <= 0) {
            return ItemStack.EMPTY;
        }

        int extractedCount = Math.min(requested, removedCount);
        if (removedCount > extractedCount) {
            restoreToContainerOrDrop(villager, container, before.copyWithCount(removedCount - extractedCount));
        }
        container.setChanged();
        return before.copyWithCount(extractedCount);
    }

    static void restoreToContainerOrDrop(Villager villager, Container container, ItemStack stack) {
        ItemStack remainder = insertIntoContainer(container, stack);
        if (!remainder.isEmpty() && villager != null) {
            villager.spawnAtLocation(remainder);
        }
    }

    static void openUsedContainers(ServerLevel level, List<ContainerCandidate> usedContainers) {
        for (ContainerCandidate candidate : usedContainers) {
            openContainerFeedback(level, candidate.pos());
        }
    }

    static void tickContainerFeedback(Iterable<ServerLevel> levels) {
        if (PENDING_CONTAINER_CLOSES.isEmpty()) {
            return;
        }
        for (ServerLevel level : levels) {
            closePendingContainers(level);
        }
    }

    private static void openContainerFeedback(ServerLevel level, BlockPos pos) {
        if (extendOpenContainerSession(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity && ChestBlockEntity.getOpenCount(level, pos) <= 0) {
            level.blockEvent(pos, state.getBlock(), ChestBlock.EVENT_SET_OPEN_COUNT, 1);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        } else if (blockEntity instanceof BarrelBlockEntity
                && state.hasProperty(BarrelBlock.OPEN)
                && !state.getValue(BarrelBlock.OPEN)) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        } else if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox && shulkerBox.isClosed()) {
            level.blockEvent(pos, state.getBlock(), ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT, 1);
            level.playSound(null, pos, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + level.random.nextFloat() * 0.1F);
        }
        if (blockEntity instanceof ChestBlockEntity
                || blockEntity instanceof BarrelBlockEntity
                || blockEntity instanceof ShulkerBoxBlockEntity) {
            scheduleContainerClose(level, pos);
        }
    }

    static void closeContainerFeedbackNow(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return;
        }
        for (BlockPos feedbackPos : connectedFeedbackPositions(level, pos)) {
            ContainerFeedbackKey key = new ContainerFeedbackKey(level.dimension(), feedbackPos.immutable());
            if (PENDING_CONTAINER_CLOSES.remove(key) != null) {
                closeContainerFeedback(level, feedbackPos);
            }
        }
    }

    private static boolean extendOpenContainerSession(ServerLevel level, BlockPos pos) {
        ContainerFeedbackKey key = new ContainerFeedbackKey(level.dimension(), pos.immutable());
        Long closeGameTime = PENDING_CONTAINER_CLOSES.get(key);
        if (closeGameTime == null || closeGameTime <= level.getGameTime()) {
            return false;
        }
        PENDING_CONTAINER_CLOSES.put(key, level.getGameTime() + CONTAINER_CLOSE_DELAY_TICKS);
        return true;
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
            if (!key.dimension().equals(level.dimension()) || entry.getValue() > gameTime) {
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

    private static List<BlockPos> connectedFeedbackPositions(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            return ContainerCandidate.connectedChestPositions(level, state, pos);
        }
        return List.of(pos.immutable());
    }

    record ContainerCandidate(BlockPos pos, Container container, List<BlockPos> positions) {
        ContainerCandidate {
            pos = pos.immutable();
            positions = normalizePositions(pos, positions);
        }

        static ContainerCandidate resolve(ServerLevel level, BlockPos pos, Container fallback) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chest) {
                Container combined = ChestBlock.getContainer(chest, state, level, pos, false);
                if (combined != null) {
                    List<BlockPos> connectedPositions = connectedChestPositions(level, state, pos);
                    return new ContainerCandidate(canonicalPosition(connectedPositions), combined, connectedPositions);
                }
            }
            return new ContainerCandidate(pos, fallback, List.of(pos));
        }

        boolean matches(BlockPos otherPos) {
            if (otherPos == null) {
                return false;
            }
            for (BlockPos storagePos : this.positions) {
                if (storagePos.equals(otherPos)) {
                    return true;
                }
            }
            return false;
        }

        boolean anyPositionMatches(java.util.function.Predicate<BlockPos> predicate) {
            java.util.function.Predicate<BlockPos> safePredicate = predicate == null ? ignored -> true : predicate;
            for (BlockPos storagePos : this.positions) {
                if (safePredicate.test(storagePos)) {
                    return true;
                }
            }
            return false;
        }

        boolean isInInteractionRange(Villager villager) {
            for (BlockPos storagePos : this.positions) {
                if (AssignedStorageService.isInInteractionRange(villager, storagePos)) {
                    return true;
                }
            }
            return false;
        }

        double distanceToSqr(BlockPos reference) {
            double best = Double.MAX_VALUE;
            for (BlockPos storagePos : this.positions) {
                best = Math.min(best, storagePos.distSqr(reference));
            }
            return best;
        }

        BlockPos nearestPosition(BlockPos reference, java.util.function.Predicate<BlockPos> predicate) {
            java.util.function.Predicate<BlockPos> safePredicate = predicate == null ? ignored -> true : predicate;
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos storagePos : this.positions) {
                if (!safePredicate.test(storagePos)) {
                    continue;
                }
                double distance = storagePos.distSqr(reference);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = storagePos;
                }
            }
            return best == null ? this.pos : best.immutable();
        }

        static List<BlockPos> connectedChestPositions(ServerLevel level, BlockState state, BlockPos pos) {
            List<BlockPos> positions = new ArrayList<>();
            positions.add(pos.immutable());
            if (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                return positions;
            }
            Direction connectedDirection = ChestBlock.getConnectedDirection(state);
            BlockPos connectedPos = pos.relative(connectedDirection);
            if (level.getBlockState(connectedPos).getBlock() instanceof ChestBlock) {
                positions.add(connectedPos.immutable());
            }
            return positions;
        }

        private static List<BlockPos> normalizePositions(BlockPos fallback, List<BlockPos> positions) {
            List<BlockPos> normalized = new ArrayList<>();
            if (positions != null) {
                for (BlockPos position : positions) {
                    if (position == null || normalized.contains(position)) {
                        continue;
                    }
                    normalized.add(position.immutable());
                }
            }
            if (normalized.isEmpty()) {
                normalized.add(fallback.immutable());
            }
            return List.copyOf(normalized);
        }

        private static BlockPos canonicalPosition(List<BlockPos> positions) {
            BlockPos best = null;
            for (BlockPos position : positions) {
                if (best == null
                        || position.getX() < best.getX()
                        || position.getX() == best.getX() && position.getY() < best.getY()
                        || position.getX() == best.getX() && position.getY() == best.getY() && position.getZ() < best.getZ()) {
                    best = position;
                }
            }
            return best == null ? BlockPos.ZERO : best.immutable();
        }
    }

    private record ContainerFeedbackKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
