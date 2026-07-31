package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge;
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
    private void villagerretaliation$filterHiredFarmerWantedItem(
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
        }
    }
}
