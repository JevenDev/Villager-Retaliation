package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearance;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearanceHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerWorkAppearanceMixin implements VillagerWorkAppearanceHolder {
    @Unique
    private static final EntityDataAccessor<String> villagerretaliation$assignedWorkRole =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.STRING);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void villagerretaliation$defineWorkAppearance(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(villagerretaliation$assignedWorkRole, "");
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void villagerretaliation$restoreWorkAppearance(CompoundTag tag, CallbackInfo callback) {
        VillagerWorkAppearance.refresh((Villager) (Object) this);
    }

    @Override
    public HiredVillagerRole villagerretaliation$assignedWorkRole() {
        return HiredVillagerRole.bySerializedName(((Villager) (Object) this).getEntityData().get(villagerretaliation$assignedWorkRole));
    }

    @Override
    public void villagerretaliation$assignedWorkRole(HiredVillagerRole role) {
        ((Villager) (Object) this).getEntityData().set(villagerretaliation$assignedWorkRole, role == null ? "" : role.serializedName());
    }
}
