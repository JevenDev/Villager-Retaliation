package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerEquipmentMending;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
public abstract class ExperienceOrbMixin {
    @Shadow private int count;
    @Shadow public int value;
    @Shadow @Nullable private Player followingPlayer;

    @Unique @Nullable private Villager villagerretaliation$followingVillager;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$preventPlayerTakingVillagerWorkExperience(
            Player player,
            CallbackInfo callback) {
        if (VillagerWorkExperience.isOwned((ExperienceOrb) (Object) this)) {
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

    @Inject(method = "scanForEntities", at = @At("TAIL"))
    private void villagerretaliation$findVillagerNeedingMending(CallbackInfo callback) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        AABB searchArea = orb.getBoundingBox().inflate(8.0D);
        Villager nearestVillager = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Villager villager : orb.level().getEntitiesOfClass(
                Villager.class,
                searchArea,
                candidate -> candidate.isAlive()
                        && (VillagerEquipmentMending.canRepair(candidate)
                        || VillagerWorkExperience.belongsTo(orb, candidate)
                        && VillagerEquipmentMending.hasMendingEquipment(candidate))
        )) {
            double distance = villager.distanceToSqr(orb);
            if (distance < 64.0D && distance < nearestDistance) {
                nearestVillager = villager;
                nearestDistance = distance;
            }
        }

        if (nearestVillager != null
                && (VillagerWorkExperience.belongsTo(orb, nearestVillager)
                || followingPlayer == null
                || nearestDistance < followingPlayer.distanceToSqr(orb))) {
            followingPlayer = null;
            villagerretaliation$followingVillager = nearestVillager;
        } else {
            villagerretaliation$followingVillager = null;
        }
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

        if (!orb.getBoundingBox().intersects(villager.getBoundingBox())) {
            return;
        }
        if (!VillagerEquipmentMending.repairWithXp(villager, value) && !canConsumeWorkExperience) {
            return;
        }

        villager.take(orb, 1);
        count--;
        if (count <= 0) {
            orb.discard();
        }
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
