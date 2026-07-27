package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerEquipmentMending;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityVillagerExperienceMixin {
    @Shadow
    protected int lastHurtByPlayerTime;

    @Shadow
    protected abstract boolean isAlwaysExperienceDropper();

    @Inject(method = "dropExperience", at = @At("TAIL"))
    private void villagerretaliation$dropExperienceForMendingVillager(
            @Nullable Entity killer,
            CallbackInfo callback) {
        LivingEntity defeated = (LivingEntity) (Object) this;
        if (!(killer instanceof Villager villager)
                || !(defeated.level() instanceof ServerLevel level)
                || this.lastHurtByPlayerTime > 0
                || this.isAlwaysExperienceDropper()
                || defeated.wasExperienceConsumed()
                || !defeated.shouldDropExperience()
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                || !VillagerEquipmentMending.hasMendingEquipment(villager)) {
            return;
        }

        int reward = EventHooks.getExperienceDrop(
                defeated,
                null,
                defeated.getExperienceReward(level, villager));
        if (reward > 0) {
            VillagerWorkExperience.spawn(level, villager, defeated.position(), reward);
        }
    }
}
