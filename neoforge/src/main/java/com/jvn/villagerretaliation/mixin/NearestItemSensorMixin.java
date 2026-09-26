package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge;
import com.jvn.villagerretaliation.villager.VillagerItemPickupReach;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NearestItemSensor.class)
public abstract class NearestItemSensorMixin {
    @Inject(method = "doTick", at = @At("RETURN"))
    private void villagerretaliation$filterWantedItem(
            ServerLevel level,
            Mob entity,
            CallbackInfo ci) {
        if (!(entity instanceof Villager villager)) {
            return;
        }
        ItemEntity wanted = villager.getBrain()
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
                .orElse(null);
        if (HiredFarmingInventoryBridge.shouldDiscardWantedItem(level, villager, wanted)) {
            villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
            wanted = null;
        }
        if (wanted == null && villager.canPickUpLoot()) {
            level.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(2.0D), item ->
                            item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
                                    && villager.wantsToPickUp(item.getItem())
                                    && VillagerItemPickupReach.isWithinReachThroughPartialBlock(villager, item, 2.25D)
                                    && !HiredFarmingInventoryBridge.shouldDiscardWantedItem(level, villager, item))
                    .stream().min(Comparator.comparingDouble(villager::distanceToSqr))
                    .ifPresent(item -> villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, item));
        }
    }
}
