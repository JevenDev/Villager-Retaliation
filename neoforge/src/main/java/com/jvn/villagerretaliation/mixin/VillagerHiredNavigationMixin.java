package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.HiredNavigationState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Villager.class)
public abstract class VillagerHiredNavigationMixin implements HiredNavigationState {
    @Unique
    private BlockPos villagerretaliation$hiredWalkTarget;

    @Override
    public BlockPos villagerretaliation$getHiredWalkTarget() {
        return this.villagerretaliation$hiredWalkTarget;
    }

    @Override
    public void villagerretaliation$setHiredWalkTarget(BlockPos target) {
        this.villagerretaliation$hiredWalkTarget = target == null ? null : target.immutable();
    }
}
