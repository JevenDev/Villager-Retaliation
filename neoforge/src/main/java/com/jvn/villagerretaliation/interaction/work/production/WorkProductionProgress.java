package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public record WorkProductionProgress(long remaining, List<ItemStack> supplies, long burn, long uses, String blocker) {
    public WorkProductionProgress { supplies = supplies.stream().map(ItemStack::copy).toList(); }

    public static WorkProductionProgress loaded(ServerLevel level, HiredProductionOrder order, HiredProductionQueue.Allocation allocation) {
        if (!allocation.started()) return new WorkProductionProgress(allocation.remaining(), List.of(), 0, 0, "");
        var machine = allocation.station() != null && level.hasChunkAt(allocation.station()) ? level.getBlockEntity(allocation.station()) : null;
        if (machine instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
            var tag = furnace.saveWithoutMetadata(level.registryAccess());
            var items = net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(tag, items, level.registryAccess());
            return furnace(order, allocation.remaining(), items, tag, !furnace.getBlockState().is(net.minecraft.world.level.block.Blocks.FURNACE));
        }
        if (machine instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand) {
            var tag = stand.saveWithoutMetadata(level.registryAccess());
            var items = net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(tag, items, level.registryAccess());
            return brewing(level, order, allocation.remaining(), items, tag);
        }
        return new WorkProductionProgress(allocation.remaining(), List.of(), 0, 0, "station_unavailable");
    }

    public static WorkProductionProgress furnace(HiredProductionOrder order, long amount, List<ItemStack> items,
                                                 CompoundTag contents, boolean fast) {
        long remaining = Math.max(0, amount - (order.matches(items.get(2)) ? items.get(2).getCount() : 0));
        long burn = (Math.max(0, contents.getInt("BurnTime")) + Math.max(0, contents.getInt("CookTime"))) * (fast ? 2L : 1L);
        return new WorkProductionProgress(remaining, List.of(items.get(0), items.get(1)), burn, 0, "");
    }

    public static WorkProductionProgress brewing(ServerLevel level, HiredProductionOrder order, long amount,
                                                 List<ItemStack> items, CompoundTag contents) {
        var route = HiredBrewingRecipeCatalog.find(level, order.recipe().brewingItem(), order.recipe().potion()).orElse(null);
        int step = route == null ? -1 : HiredBrewingRecipeCatalog.currentStep(level, items, route);
        if (step < 0) return new WorkProductionProgress(amount, List.of(), 0, 0, "station_batch_missing");
        var supplies = new ArrayList<ItemStack>();
        int bottles = (int) items.subList(0, 3).stream().filter(stack -> !stack.isEmpty()).count();
        if (bottles > 0) supplies.add(PotionContents.createItemStack(Items.POTION, Potions.WATER).copyWithCount(bottles));
        for (int index = 0; index < step; index++) supplies.add(new ItemStack(route.ingredients().get(index)));
        supplies.add(items.get(3));
        supplies.add(items.get(4));
        long uses = step + Math.max(0, contents.getInt("Fuel")) + (contents.getInt("BrewTime") > 0 ? 1 : 0);
        return new WorkProductionProgress(amount, supplies, 0, uses, "");
    }
}
