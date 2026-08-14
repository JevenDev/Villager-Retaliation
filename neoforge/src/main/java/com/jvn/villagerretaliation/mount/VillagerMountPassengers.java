package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.toucanlib.riding.ToucanPassengerApi;
import com.jvn.toucanlib.riding.ToucanPassengerLayout;
import com.jvn.toucanlib.riding.ToucanPassengerLayouts;
import com.jvn.toucanlib.riding.ToucanPassengerSeat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** Villager Retaliation's mount policy on top of ToucanLib's passenger mechanics. */
public final class VillagerMountPassengers {
    public static final ResourceLocation LAYOUT_ID = VillagerRetaliation.id("assigned_horse_seats");
    private static final double VILLAGER_VERTICAL_OFFSET = -0.6D;
    private static final ToucanPassengerLayout LAYOUT = new ToucanPassengerLayout() {
        @Override
        public int seatCapacity(Entity vehicle) {
            if (!(vehicle instanceof AbstractHorse horse) || !horse.isAlive() || horse.isBaby()) {
                return 0;
            }
            // Preserve the established assignment policy: normal horses carry a pair while the
            // other assignable horse-family mounts retain their vanilla single assignment.
            return horse.getType() == EntityType.HORSE ? 2 : 1;
        }

        @Override
        public Vec3 seatOffset(Entity vehicle, Entity passenger, ToucanPassengerSeat seat) {
            if (!(vehicle instanceof AbstractHorse horse)) {
                return Vec3.ZERO;
            }
            double vertical = passenger instanceof Villager ? VILLAGER_VERTICAL_OFFSET : 0.0D;
            return seat == ToucanPassengerSeat.PASSENGER
                    ? new Vec3(0.0D, vertical + 0.04D * horse.getScale(),
                    -0.56D * horse.getScale())
                    : new Vec3(0.0D, vertical, 0.0D);
        }

        @Override
        public boolean canMount(Entity vehicle, Entity passenger) {
            return vehicle instanceof AbstractHorse horse && !horse.isBaby();
        }
    };

    private VillagerMountPassengers() {
    }

    public static void init() {
        ToucanPassengerLayouts.register(LAYOUT_ID, LAYOUT);
    }

    public static int seatCapacity(AbstractHorse horse) {
        return ToucanPassengerApi.seatCapacity(LAYOUT_ID, horse);
    }

    public static boolean supportsDriver(AbstractHorse horse) {
        return ToucanPassengerApi.supportsSeat(LAYOUT_ID, horse, ToucanPassengerSeat.DRIVER);
    }

    public static boolean supportsPassenger(AbstractHorse horse) {
        return ToucanPassengerApi.supportsSeat(LAYOUT_ID, horse, ToucanPassengerSeat.PASSENGER);
    }

    public static boolean tryMountAvailableSeat(AbstractHorse horse, Entity rider) {
        return ToucanPassengerApi.tryMountAvailable(LAYOUT_ID, horse, rider);
    }

    public static boolean tryDismount(AbstractHorse horse, Entity rider) {
        return ToucanPassengerApi.tryDismount(horse, rider);
    }

    public static boolean tryTakeDriverSeat(AbstractHorse horse, Entity rider) {
        return ToucanPassengerApi.tryTakeDriverSeat(LAYOUT_ID, horse, rider);
    }

    public static Entity occupant(AbstractHorse horse, boolean passengerSeat) {
        return ToucanPassengerApi.occupant(
                horse,
                passengerSeat ? ToucanPassengerSeat.PASSENGER : ToucanPassengerSeat.DRIVER);
    }

    public static boolean isRearPassenger(AbstractHorse horse, Entity rider) {
        return rider != null && occupant(horse, true) == rider;
    }

    public static boolean managesPassengerPosition(AbstractHorse horse, Entity rider) {
        return ToucanPassengerApi.managesPassenger(horse, rider);
    }
}
