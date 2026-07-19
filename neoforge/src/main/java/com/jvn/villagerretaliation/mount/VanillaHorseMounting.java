package com.jvn.villagerretaliation.mount;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;

/**
 * Vanilla first-passenger mounting used by mob jockeys.
 *
 * <p>A {@link Villager} is a {@code Mob}, so once it is the horse's first passenger Minecraft
 * treats it as the controlling passenger. The rider's navigation and move control then delegate
 * to the horse in the same way as vanilla hostile mount riders.</p>
 */
final class VanillaHorseMounting {
    private VanillaHorseMounting() {
    }

    static Entity rider(AbstractHorse horse) {
        return horse == null ? null : horse.getFirstPassenger();
    }

    static boolean tryMount(AbstractHorse horse, Villager villager) {
        return horse != null
                && villager != null
                && horse.level() == villager.level()
                && horse.isAlive()
                && villager.isAlive()
                && horse.getPassengers().isEmpty()
                && villager.getVehicle() == null
                && villager.startRiding(horse, true);
    }

    static boolean tryDismount(AbstractHorse horse, Villager villager) {
        if (horse == null || villager == null || villager.getVehicle() != horse) {
            return false;
        }
        villager.stopRiding();
        return villager.getVehicle() != horse;
    }

    static boolean tryTakeOver(AbstractHorse horse, ServerPlayer player) {
        if (horse == null
                || player == null
                || horse.level() != player.level()
                || !horse.isAlive()
                || !player.isAlive()
                || player.getVehicle() != null
                || !horse.isSaddled()) {
            return false;
        }
        Entity previousRider = rider(horse);
        if (!(previousRider instanceof Villager villager)) {
            return false;
        }

        villager.stopRiding();
        if (villager.getVehicle() == horse) {
            return false;
        }
        if (player.startRiding(horse, true)) {
            return true;
        }

        // A mount event may reject the player after the villager has dismounted. Restore the
        // assigned rider so a canceled takeover cannot strand the pair in an inconsistent state.
        villager.startRiding(horse, true);
        return false;
    }
}
