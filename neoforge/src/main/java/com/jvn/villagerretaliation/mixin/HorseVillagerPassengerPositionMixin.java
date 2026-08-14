package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.mount.VillagerMountPassengers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives villager horse riders the same vertical seat correction as vanilla illager riders. */
@Mixin(AbstractHorse.class)
public abstract class HorseVillagerPassengerPositionMixin {
    private static final double VILLAGER_HORSE_RIDING_OFFSET = -0.6D;

    @Inject(method = "positionRider", at = @At("TAIL"))
    private void villagerretaliation$lowerVillagerHorseRider(
            Entity passenger,
            Entity.MoveFunction callback,
            CallbackInfo callbackInfo) {
        AbstractHorse horse = (AbstractHorse) (Object) this;
        if (passenger instanceof Villager
                && horse.getFirstPassenger() == passenger
                && !VillagerMountPassengers.managesPassengerPosition(horse, passenger)) {
            passenger.setPos(
                    passenger.getX(),
                    passenger.getY() + VILLAGER_HORSE_RIDING_OFFSET,
                    passenger.getZ());
        }
    }
}
