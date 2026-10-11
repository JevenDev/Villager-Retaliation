package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.repair.SmithRepairAnimationState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerSmithRepairMixin implements SmithRepairAnimationState {
    @Unique
    private static final EntityDataAccessor<Optional<BlockPos>> villagerretaliation$repairStation =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    @Unique
    private static final EntityDataAccessor<ItemStack> villagerretaliation$repairItem =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.ITEM_STACK);
    @Unique
    private static final EntityDataAccessor<Long> villagerretaliation$repairStarted =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.LONG);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void villagerretaliation$defineRepairAnimation(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(villagerretaliation$repairStation, Optional.empty());
        builder.define(villagerretaliation$repairItem, ItemStack.EMPTY);
        builder.define(villagerretaliation$repairStarted, -1L);
    }

    @Override
    public BlockPos villagerretaliation$repairStation() {
        return ((Villager) (Object) this).getEntityData().get(villagerretaliation$repairStation).orElse(null);
    }

    @Override
    public ItemStack villagerretaliation$repairItem() {
        return ((Villager) (Object) this).getEntityData().get(villagerretaliation$repairItem);
    }

    @Override
    public long villagerretaliation$repairStarted() {
        return ((Villager) (Object) this).getEntityData().get(villagerretaliation$repairStarted);
    }

    @Override
    public void villagerretaliation$repairAnimation(BlockPos station, ItemStack item, long started) {
        var data = ((Villager) (Object) this).getEntityData();
        data.set(villagerretaliation$repairStation, Optional.ofNullable(station));
        data.set(villagerretaliation$repairItem, item.copy());
        data.set(villagerretaliation$repairStarted, started);
    }
}
