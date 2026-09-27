package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementGroundExperienceService;
import com.jvn.villagerretaliation.villager.VillagerEquipmentMending;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin implements com.jvn.villagerretaliation.villager.ExperienceOrbWorkTarget {
    @Shadow private int count;
    @Shadow @Nullable private Player followingPlayer;

    @Unique @Nullable private Villager villagerretaliation$followingVillager;

    @Override
    public Villager villagerretaliation$workExperienceTarget() {
        return this.villagerretaliation$followingVillager;
    }

    @Override
    public void villagerretaliation$workExperienceTarget(Villager villager) {
        this.villagerretaliation$followingVillager = villager;
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$preventPlayerTakingVillagerWorkExperience(
            Player player,
            CallbackInfo callback) {
        if (!SettlementGroundExperienceService.onActive((ExperienceOrb) (Object) this)
                || VillagerWorkExperience.isOwned((ExperienceOrb) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void villagerretaliation$keepWorkExperienceSeparate(
            ExperienceOrb other,
            CallbackInfoReturnable<Boolean> callback) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        if (!VillagerWorkExperience.mayMerge(orb, other)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void villagerretaliation$preventVanillaExperienceMergingIntoWorkExperience(
            ExperienceOrb candidate,
            int id,
            int value,
            CallbackInfoReturnable<Boolean> callback) {
        if (VillagerWorkExperience.isOwned(candidate)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void villagerretaliation$observePlayerExperiencePickup(Player player, CallbackInfo callback) {
        SettlementGroundExperienceService.onChanged((ExperienceOrb) (Object) this);
    }

    @Inject(method = "merge", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$reconcileExperienceBeforeMerge(ExperienceOrb other, CallbackInfo callback) {
        if (!SettlementGroundExperienceService.onActive((ExperienceOrb) (Object) this)
                || !SettlementGroundExperienceService.onActive(other)) callback.cancel();
    }

    @Inject(method = "merge", at = @At("RETURN"))
    private void villagerretaliation$observeMergedExperience(ExperienceOrb other, CallbackInfo callback) {
        SettlementGroundExperienceService.onChanged((ExperienceOrb) (Object) this);
    }

    @Inject(method = "tryMergeToExisting", at = @At("HEAD"))
    private static void villagerretaliation$reconcileExperienceBeforeAward(net.minecraft.server.level.ServerLevel level,
                                                                         Vec3 position, int value, CallbackInfoReturnable<Boolean> callback) {
        SettlementGroundExperienceService.beforeAward(level, position);
    }

    @Inject(method = "award", at = @At("RETURN"))
    private static void villagerretaliation$observeAwardedExperience(net.minecraft.server.level.ServerLevel level,
                                                                   Vec3 position, int value, CallbackInfo callback) {
        SettlementGroundExperienceService.onAwarded(level, position);
    }

    @Inject(method = "scanForEntities", at = @At("TAIL"))
    private void villagerretaliation$findVillagerNeedingMending(CallbackInfo callback) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        villagerretaliation$followingVillager = VillagerWorkExperience.mendingTarget(orb, followingPlayer,
                orb.level().getEntitiesOfClass(Villager.class, orb.getBoundingBox().inflate(8.0D)));
        if (villagerretaliation$followingVillager != null) followingPlayer = null;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$repairVillagerArmorOnContact(CallbackInfo callback) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        Villager villager = villagerretaliation$followingVillager;
        boolean canConsumeWorkExperience = villager != null
                && VillagerWorkExperience.belongsTo(orb, villager)
                && VillagerEquipmentMending.hasMendingEquipment(villager);
        if (villager == null
                || !villager.isAlive()
                || villager.level() != orb.level()
                || villager.distanceToSqr(orb) >= 64.0D
                || !VillagerEquipmentMending.canRepair(villager) && !canConsumeWorkExperience) {
            villagerretaliation$followingVillager = null;
            return;
        }

        if (!VillagerWorkExperience.mendOnContact(orb, villager)) return;

        villager.take(orb, 1);
        count--;
        if (count <= 0) {
            orb.discard();
        }
        SettlementGroundExperienceService.onChanged(orb);
        callback.cancel();
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private void villagerretaliation$attractToVillager(CallbackInfo callback) {
        Villager villager = villagerretaliation$followingVillager;
        if (villager == null) {
            return;
        }

        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        Vec3 offset = new Vec3(
                villager.getX() - orb.getX(),
                villager.getY() + villager.getEyeHeight() / 2.0D - orb.getY(),
                villager.getZ() - orb.getZ()
        );
        double distance = offset.lengthSqr();
        if (distance > 0.0D && distance < 64.0D) {
            double pull = 1.0D - Math.sqrt(distance) / 8.0D;
            orb.setDeltaMovement(orb.getDeltaMovement().add(offset.normalize().scale(pull * pull * 0.1D)));
        }
    }
}
