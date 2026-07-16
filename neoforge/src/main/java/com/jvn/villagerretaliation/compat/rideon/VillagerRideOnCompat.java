package com.jvn.villagerretaliation.compat.rideon;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Reflection-isolated access to Ride On API v2. */
public final class VillagerRideOnCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "rideon";
    private static final int REQUIRED_API_VERSION = 2;
    private static final Vec3 VILLAGER_RIDING_OFFSET = new Vec3(0.0D, -0.6D, 0.0D);
    // Ride On supplies the rear seat's native -0.56 Z anchor. Add pair clearance behind that
    // anchor; the driver must remain centered on the saddle instead of entering the horse's neck.
    private static final Vec3 VILLAGER_PAIR_REAR_OFFSET = new Vec3(0.0D, -0.6D, -0.55D);
    private static volatile boolean initialized;
    private static volatile Bridge bridge;

    private VillagerRideOnCompat() {
    }

    public static boolean available() {
        return bridge() != null;
    }

    public static boolean supportsDriver(AbstractHorse horse) {
        Bridge active = bridge();
        return active != null && active.invokeBoolean(active.supportsSeat(), horse, active.driverSeat());
    }

    public static boolean supportsPassenger(AbstractHorse horse) {
        Bridge active = bridge();
        return active != null && active.invokeBoolean(active.supportsSeat(), horse, active.passengerSeat());
    }

    public static boolean tryMountDriver(AbstractHorse horse, Entity rider) {
        Bridge active = bridge();
        if (active == null || !active.invokeBoolean(
                active.tryMountWithOffset(), horse, rider, active.driverSeat(), VILLAGER_RIDING_OFFSET)) {
            return false;
        }
        normalizeVillagerSeatOffsets(active, horse);
        return true;
    }

    public static boolean tryMountPassenger(AbstractHorse horse, Entity rider) {
        Bridge active = bridge();
        if (active == null) {
            return false;
        }
        Vec3 ridingOffset = occupant(active, horse, false) instanceof Villager && rider instanceof Villager
                ? VILLAGER_PAIR_REAR_OFFSET
                : VILLAGER_RIDING_OFFSET;
        if (!active.invokeBoolean(active.tryMountWithOffset(), horse, rider, active.passengerSeat(), ridingOffset)) {
            return false;
        }
        normalizeVillagerSeatOffsets(active, horse);
        return true;
    }

    public static boolean tryMountAvailableSeat(AbstractHorse horse, Entity rider) {
        if (occupant(horse, false) == null) {
            return tryMountDriver(horse, rider);
        }
        return supportsPassenger(horse)
                && occupant(horse, true) == null
                && tryMountPassenger(horse, rider);
    }

    public static boolean tryDismount(AbstractHorse horse, Entity rider) {
        Bridge active = bridge();
        if (active == null || !active.invokeBoolean(active.tryDismount(), horse, rider)) {
            return false;
        }
        normalizeVillagerSeatOffsets(active, horse);
        return true;
    }

    public static boolean tryTakeDriverSeat(AbstractHorse horse, Entity rider) {
        Bridge active = bridge();
        if (active == null || !active.invokeBoolean(active.tryTakeDriverSeat(), horse, rider)) {
            return false;
        }
        normalizeVillagerSeatOffsets(active, horse);
        return true;
    }

    public static Entity occupant(AbstractHorse horse, boolean passengerSeat) {
        Bridge active = bridge();
        return active == null ? null : occupant(active, horse, passengerSeat);
    }

    private static Entity occupant(Bridge active, AbstractHorse horse, boolean passengerSeat) {
        Object result = active.invoke(
                active.occupant(),
                horse,
                passengerSeat ? active.passengerSeat() : active.driverSeat());
        return result instanceof Entity entity ? entity : null;
    }

    private static void normalizeVillagerSeatOffsets(Bridge active, AbstractHorse horse) {
        Entity driver = occupant(active, horse, false);
        Entity passenger = occupant(active, horse, true);
        boolean villagerPair = driver instanceof Villager && passenger instanceof Villager;
        if (driver instanceof Villager) {
            active.invokeBoolean(
                    active.setSeatOffset(),
                    horse,
                    driver,
                    VILLAGER_RIDING_OFFSET);
        }
        if (passenger instanceof Villager) {
            active.invokeBoolean(
                    active.setSeatOffset(),
                    horse,
                    passenger,
                    villagerPair ? VILLAGER_PAIR_REAR_OFFSET : VILLAGER_RIDING_OFFSET);
        }
    }

    private static Bridge bridge() {
        if (!initialized) {
            synchronized (VillagerRideOnCompat.class) {
                if (!initialized) {
                    bridge = loadBridge();
                    initialized = true;
                }
            }
        }
        return bridge;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Bridge loadBridge() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("com.jvn.rideon.api.RideOnApi");
            Field versionField = apiClass.getField("API_VERSION");
            if (versionField.getInt(null) < REQUIRED_API_VERSION) {
                LOGGER.warn("Ride On is installed, but its API is older than version {}. Villager mounts are disabled.",
                        REQUIRED_API_VERSION);
                return null;
            }
            Class<? extends Enum> seatClass = (Class<? extends Enum>) Class.forName("com.jvn.rideon.api.HorseSeat");
            Object driver = Enum.valueOf(seatClass, "DRIVER");
            Object passenger = Enum.valueOf(seatClass, "PASSENGER");
            return new Bridge(
                    driver,
                    passenger,
                    apiClass.getMethod("supportsSeat", AbstractHorse.class, seatClass),
                    apiClass.getMethod("tryMount", AbstractHorse.class, Entity.class, seatClass),
                    apiClass.getMethod("tryMount", AbstractHorse.class, Entity.class, seatClass, Vec3.class),
                    apiClass.getMethod("tryDismount", AbstractHorse.class, Entity.class),
                    apiClass.getMethod("tryTakeDriverSeat", AbstractHorse.class, Entity.class),
                    apiClass.getMethod("setSeatOffset", AbstractHorse.class, Entity.class, Vec3.class),
                    apiClass.getMethod("occupant", AbstractHorse.class, seatClass)
            );
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Ride On API v2 could not be initialized. Villager mounts are disabled.", exception);
            return null;
        }
    }

    private record Bridge(
            Object driverSeat,
            Object passengerSeat,
            Method supportsSeat,
            Method tryMount,
            Method tryMountWithOffset,
            Method tryDismount,
            Method tryTakeDriverSeat,
            Method setSeatOffset,
            Method occupant) {
        private Object invoke(Method method, Object... arguments) {
            try {
                return method.invoke(null, arguments);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.debug("Ride On API call {} failed", method.getName(), exception);
                return null;
            }
        }

        private boolean invokeBoolean(Method method, Object... arguments) {
            return Boolean.TRUE.equals(invoke(method, arguments));
        }
    }
}
