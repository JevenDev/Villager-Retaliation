package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventoryHolder;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Villager.class)
public abstract class VillagerJobInventoryMixin implements HiredJobInventoryHolder {
    @Unique
    private HiredJobInventory villagerretaliation$jobInventory;

    @Override
    public HiredJobInventory villagerretaliation$getOrCreateJobInventory() {
        if (this.villagerretaliation$jobInventory == null) {
            this.villagerretaliation$jobInventory = new HiredJobInventory((Villager) (Object) this);
        }
        return this.villagerretaliation$jobInventory;
    }

    @Override
    public void villagerretaliation$clearJobInventory() {
        this.villagerretaliation$jobInventory = null;
    }
}
