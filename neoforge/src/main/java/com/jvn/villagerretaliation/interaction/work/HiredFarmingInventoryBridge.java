package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.SpecialPlantable;

public final class HiredFarmingInventoryBridge {
    private static final int PLANTING_RESERVE = 8;
    private static final ThreadLocal<JobPlantingContainer> ACTIVE_PLANTING_CONTAINER = new ThreadLocal<>();

    private HiredFarmingInventoryBridge() {
    }

    public static Boolean wantsToPickUp(ServerLevel level, Villager villager, ItemStack stack) {
        HiredWorkSession session = activeFarmingSession(level, villager);
        if (session == null) {
            return null;
        }
        if (!isFarmPickupItem(villager, stack)) {
            return false;
        }
        return canAcceptFarmPickup(villager, session.inventory(), stack)
                && HiredVillagerWorkService.isInsideEffectiveWorkArea(
                level,
                villager,
                HiredVillagerRole.FARMING,
                session.context(),
                villager.blockPosition());
    }

    public static boolean shouldDiscardWantedItem(ServerLevel level, Villager villager, ItemEntity itemEntity) {
        HiredWorkSession session = activeFarmingSession(level, villager);
        if (session == null || itemEntity == null) {
            return false;
        }
        ItemStack stack = itemEntity.getItem();
        return !isFarmPickupItem(villager, stack)
                || !canAcceptFarmPickup(villager, session.inventory(), stack)
                || !HiredVillagerWorkService.isInsideEffectiveWorkArea(
                level,
                villager,
                HiredVillagerRole.FARMING,
                session.context(),
                itemEntity.blockPosition());
    }

    public static boolean capturePickup(ServerLevel level, Villager villager, ItemEntity itemEntity) {
        HiredWorkSession session = activeFarmingSession(level, villager);
        if (session == null) {
            return false;
        }
        ItemStack stack = itemEntity.getItem();
        BlockPos itemPos = itemEntity.blockPosition();
        if (!isFarmPickupItem(villager, stack)
                || !HiredVillagerWorkService.isInsideEffectiveWorkArea(
                level,
                villager,
                HiredVillagerRole.FARMING,
                session.context(),
                itemPos)) {
            return true;
        }

        ItemStack remainder = insertFarmPickup(villager, session.inventory(), stack.copy());
        int moved = stack.getCount() - remainder.getCount();
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(session.context(), "output_inventory_full", 0L);
            return true;
        }

        villager.onItemPickup(itemEntity);
        villager.take(itemEntity, moved);
        if (remainder.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remainder);
        }
        HiredWorkerBrain.clearFailure(session.context());
        return true;
    }

    public static int sweepPersonalFarmItemsToJobInventory(Villager villager, HiredWorkContext context) {
        SimpleContainer personalInventory = villager.getInventory();
        int moved = 0;
        for (int slot = 0; slot < personalInventory.getContainerSize(); slot++) {
            ItemStack stack = personalInventory.getItem(slot);
            if (!isFarmPickupItem(villager, stack)) {
                continue;
            }
            ItemStack remainder = insertFarmPickup(villager, context.inventory(), stack.copy());
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted <= 0) {
                continue;
            }
            stack.shrink(inserted);
            if (stack.isEmpty()) {
                personalInventory.setItem(slot, ItemStack.EMPTY);
            }
            moved += inserted;
        }
        if (moved > 0) {
            personalInventory.setChanged();
        }
        return moved;
    }

    public static boolean hasJobPlantingItem(Villager villager, HiredWorkContext context) {
        return hasPlantingItem(villager, context.inventory());
    }

    public static boolean hasPlantingItem(ServerLevel level, Villager villager) {
        HiredWorkSession session = activeFarmingSession(level, villager);
        return session == null ? villager.hasFarmSeeds() : hasPlantingItem(villager, session.inventory());
    }

    public static SimpleContainer plantingInventory(ServerLevel level, Villager villager) {
        HiredWorkSession session = activeFarmingSession(level, villager);
        if (session == null) {
            return villager.getInventory();
        }
        JobPlantingContainer container = new JobPlantingContainer(session.inventory(), plantingSlots(villager, session.inventory()));
        ACTIVE_PLANTING_CONTAINER.set(container);
        return container;
    }

    public static void finishPlantingInventory() {
        JobPlantingContainer container = ACTIVE_PLANTING_CONTAINER.get();
        ACTIVE_PLANTING_CONTAINER.remove();
        if (container != null) {
            container.commitConsumedItems();
        }
    }

    public static boolean storeFarmDrops(Villager villager, HiredJobInventory inventory, List<ItemStack> drops) {
        boolean storedAll = true;
        for (ItemStack drop : drops) {
            ItemStack remainder = insertFarmPickup(villager, inventory, drop.copy());
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
                storedAll = false;
            }
        }
        return storedAll;
    }

    public static boolean plantFromJobInventory(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos cropPos) {
        HiredJobInventory inventory = context.inventory();
        for (int slot : plantingSlots(villager, inventory)) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (placePlant(level, villager, cropPos, stack)) {
                inventory.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    public static ItemStack plantingItem(Villager villager, HiredWorkContext context) {
        for (int slot : plantingSlots(villager, context.inventory())) {
            ItemStack stack = context.inventory().getItem(slot);
            if (!stack.isEmpty()) {
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean isFarmPickupItem(Villager villager, ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.WHEAT)
                || stack.is(Items.BEETROOT)
                || stack.is(Items.CARROT)
                || stack.is(Items.POTATO)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.TORCHFLOWER_SEEDS)
                || stack.is(Items.PITCHER_POD)
                || isPlantingItem(villager, stack));
    }

    private static boolean placePlant(ServerLevel level, Villager villager, BlockPos cropPos, ItemStack stack) {
        boolean planted = false;
        if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && stack.getItem() instanceof BlockItem blockItem) {
            BlockState plantedState = blockItem.getBlock().defaultBlockState();
            if (plantedState.canSurvive(level, cropPos)) {
                level.setBlockAndUpdate(cropPos, plantedState);
                level.gameEvent(GameEvent.BLOCK_PLACE, cropPos, GameEvent.Context.of(villager, plantedState));
                planted = true;
            }
        } else if (stack.getItem() instanceof SpecialPlantable specialPlantable
                && specialPlantable.villagerCanPlantItem(villager)
                && specialPlantable.canPlacePlantAtPosition(stack, level, cropPos, Direction.DOWN)) {
            specialPlantable.spawnPlantAtPosition(stack, level, cropPos, Direction.DOWN);
            planted = true;
        }
        if (planted) {
            level.playSound(
                    null,
                    cropPos.getX(),
                    cropPos.getY(),
                    cropPos.getZ(),
                    SoundEvents.CROP_PLANTED,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F);
        }
        return planted;
    }

    private static HiredWorkSession activeFarmingSession(ServerLevel level, Villager villager) {
        if (VillagerRetaliationVillagerCombatUtil.isThreatened(villager)
                || !HiredVillagerContractService.isHired(level, villager)
                || HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.FARMING) {
            return null;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        if (session.worker() == null || !session.state().getBoolean("Enabled")) {
            return null;
        }
        return session;
    }

    private static ItemStack insertFarmPickup(Villager villager, HiredJobInventory inventory, ItemStack stack) {
        ItemStack remainder = stack.copy();
        if (isPlantingItem(villager, remainder)) {
            int reserveNeeded = Math.max(0, PLANTING_RESERVE - countPlantingItems(villager, inventory.supplySlots(), inventory));
            if (reserveNeeded > 0) {
                ItemStack reserve = remainder.copyWithCount(Math.min(reserveNeeded, remainder.getCount()));
                ItemStack reserveRemainder = inventory.insertPlainSupply(reserve);
                int reserved = reserve.getCount() - reserveRemainder.getCount();
                if (reserved > 0) {
                    remainder.shrink(reserved);
                }
            }
        }
        if (!remainder.isEmpty()) {
            remainder = inventory.insertPlainOutput(remainder);
        }
        return remainder;
    }

    private static boolean canAcceptFarmPickup(Villager villager, HiredJobInventory inventory, ItemStack stack) {
        ItemStack sample = stack.copyWithCount(1);
        if (isPlantingItem(villager, sample)
                && countPlantingItems(villager, inventory.supplySlots(), inventory) < PLANTING_RESERVE
                && inventory.canStorePlainSupplies(List.of(sample))) {
            return true;
        }
        return inventory.canStorePlainOutputs(List.of(sample));
    }

    private static boolean hasPlantingItem(Villager villager, HiredJobInventory inventory) {
        return countPlantingItems(villager, inventory.supplySlots(), inventory) > 0
                || countPlantingItems(villager, inventory.outputSlots(), inventory) > 0;
    }

    private static int countPlantingItems(Villager villager, List<Integer> slots, HiredJobInventory inventory) {
        int count = 0;
        for (int slot : slots) {
            ItemStack stack = inventory.getItem(slot);
            if (isPlantingItem(villager, stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean isPlantingItem(Villager villager, ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
                || stack.getItem() instanceof SpecialPlantable specialPlantable
                && specialPlantable.villagerCanPlantItem(villager));
    }

    private static int[] plantingSlots(Villager villager, HiredJobInventory inventory) {
        ArrayList<Integer> slots = new ArrayList<>();
        addPlantingSlots(villager, inventory, inventory.supplySlots(), slots);
        addPlantingSlots(villager, inventory, inventory.outputSlots(), slots);
        int[] result = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            result[i] = slots.get(i);
        }
        return result;
    }

    private static void addPlantingSlots(
            Villager villager,
            HiredJobInventory inventory,
            List<Integer> sourceSlots,
            List<Integer> result) {
        for (int slot : sourceSlots) {
            if (isPlantingItem(villager, inventory.getItem(slot))) {
                result.add(slot);
            }
        }
    }

    private static final class JobPlantingContainer extends SimpleContainer {
        private final HiredJobInventory inventory;
        private final int[] inventorySlots;
        private final ItemStack[] originalStacks;
        private final ItemStack[] plantingStacks;

        private JobPlantingContainer(HiredJobInventory inventory, int[] inventorySlots) {
            super(inventorySlots.length);
            this.inventory = inventory;
            this.inventorySlots = inventorySlots;
            this.originalStacks = new ItemStack[inventorySlots.length];
            this.plantingStacks = new ItemStack[inventorySlots.length];
            for (int i = 0; i < inventorySlots.length; i++) {
                ItemStack stack = inventory.getItem(inventorySlots[i]).copy();
                this.originalStacks[i] = stack.copy();
                this.plantingStacks[i] = stack;
            }
        }

        @Override
        public ItemStack getItem(int index) {
            return index >= 0 && index < this.plantingStacks.length ? this.plantingStacks[index] : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index >= 0 && index < this.plantingStacks.length) {
                this.plantingStacks[index] = stack;
            }
        }

        @Override
        public int getContainerSize() {
            return this.plantingStacks.length;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : this.plantingStacks) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private void commitConsumedItems() {
            for (int i = 0; i < this.inventorySlots.length; i++) {
                ItemStack original = this.originalStacks[i];
                ItemStack current = this.plantingStacks[i];
                if (original.isEmpty()) {
                    continue;
                }
                int consumed = original.getCount() - (ItemStack.isSameItemSameComponents(original, current) ? current.getCount() : 0);
                if (consumed > 0) {
                    this.inventory.removeItem(this.inventorySlots[i], consumed);
                }
            }
        }
    }
}
