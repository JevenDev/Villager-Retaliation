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

/** Reflection-isolated access to Ride On's current dual-rider API. */
public final class VillagerRideOnCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "rideon";
    private static final int SUPPORTED_API_VERSION = 1;
    private static final Vec3 VILLAGER_DRIVER_OFFSET = new Vec3(0.0D, -0.6D, 0.0D);
    // Ride On already moves its rear seat 0.56 blocks behind the driver. Only lower the
    // villager model here so the custom integration offset does not push it onto the rump.
    private static final Vec3 VILLAGER_PASSENGER_OFFSET = new Vec3(0.0D, -0.6D, 0.0D);
    private static volatile boolean initialized;
    private static volatile Bridge bridge;

    private VillagerRideOnCompat() {
    }

    public static boolean available() {
        return bridge() != null;
    }

    public static int seatCapacity(AbstractHorse horse) {
        Bridge active = bridge();
        if (active == null) {
            return 1;
        }
        Object result = active.invoke(active.seatCapacity(), horse);
        if (result instanceof Integer capacity) {
            return Math.clamp(capacity, 0, 2);
        }
        return 0;
    }

    public static boolean supportsDriver(AbstractHorse horse) {
        Bridge active = bridge();
        return active != null && active.invokeBoolean(active.supportsSeat(), horse, active.driverSeat());
    }

    public static boolean supportsPassenger(AbstractHorse horse) {
        Bridge active = bridge();
        return active != null && active.invokeBoolean(active.supportsSeat(), horse, active.passengerSeat());
    }

    public static boolean tryMountAvailableSeat(AbstractHorse horse, Villager villager) {
        if (occupant(horse, false) == null) {
            return tryMount(horse, villager, false, VILLAGER_DRIVER_OFFSET);
        }
        return occupant(horse, true) == null
                && tryMount(horse, villager, true, VILLAGER_PASSENGER_OFFSET);
    }

    public static boolean tryDismount(AbstractHorse horse, Entity rider) {
        Bridge active = bridge();
        return active != null && active.invokeBoolean(active.tryDismount(), horse, rider);
    }

    public static boolean tryTakeDriverSeat(AbstractHorse horse, Entity rider) {
        Bridge active = bridge();
        if (active == null) {
            return false;
        }
        boolean taken = active.invokeBoolean(active.tryTakeDriverSeat(), horse, rider);
        if (taken) {
            normalizeVillagerOffsets(active, horse);
        }
        return taken;
    }

    public static Entity occupant(AbstractHorse horse, boolean passengerSeat) {
        Bridge active = bridge();
        if (active == null || horse == null) {
            return null;
        }
        Object result = active.invoke(
                active.occupant(),
                horse,
                passengerSeat ? active.passengerSeat() : active.driverSeat());
        return result instanceof Entity entity ? entity : null;
    }

    public static boolean isRearPassenger(AbstractHorse horse, Entity rider) {
        return rider != null && occupant(horse, true) == rider;
    }

    /** True when Ride On, rather than VR's vanilla fallback mixin, positions this passenger. */
    public static boolean managesPassengerPosition(AbstractHorse horse, Entity rider) {
        return available()
                && rider != null
                && (occupant(horse, false) == rider || occupant(horse, true) == rider);
    }

    private static boolean tryMount(
            AbstractHorse horse,
            Entity rider,
            boolean passengerSeat,
            Vec3 offset) {
        Bridge active = bridge();
        if (active == null || !active.invokeBoolean(
                active.tryMountWithOffset(),
                horse,
                rider,
                passengerSeat ? active.passengerSeat() : active.driverSeat(),
                offset)) {
            return false;
        }
        normalizeVillagerOffsets(active, horse);
        return true;
    }

    private static void normalizeVillagerOffsets(Bridge active, AbstractHorse horse) {
        Entity driver = occupant(horse, false);
        Entity passenger = occupant(horse, true);
        if (driver instanceof Villager) {
            active.invokeBoolean(active.setSeatOffset(), horse, driver, VILLAGER_DRIVER_OFFSET);
        }
        if (passenger instanceof Villager) {
            active.invokeBoolean(active.setSeatOffset(), horse, passenger, VILLAGER_PASSENGER_OFFSET);
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
            int apiVersion = versionField.getInt(null);
            if (apiVersion != SUPPORTED_API_VERSION) {
                LOGGER.warn("Ride On API {} does not match supported API {}.", apiVersion, SUPPORTED_API_VERSION);
                return null;
            }
            Class<? extends Enum> seatClass = (Class<? extends Enum>) Class.forName("com.jvn.rideon.api.HorseSeat");
            Object driver = Enum.valueOf(seatClass, "DRIVER");
            Object passenger = Enum.valueOf(seatClass, "PASSENGER");
            return new Bridge(
                    driver,
                    passenger,
                    apiClass.getMethod("supportsSeat", AbstractHorse.class, seatClass),
                    apiClass.getMethod("tryMount", AbstractHorse.class, Entity.class, seatClass, Vec3.class),
                    apiClass.getMethod("tryDismount", AbstractHorse.class, Entity.class),
                    apiClass.getMethod("tryTakeDriverSeat", AbstractHorse.class, Entity.class),
                    apiClass.getMethod("setSeatOffset", AbstractHorse.class, Entity.class, Vec3.class),
                    apiClass.getMethod("occupant", AbstractHorse.class, seatClass),
                    apiClass.getMethod("seatCapacity", AbstractHorse.class));
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Ride On API could not be initialized; VR will use vanilla single-seat mounting.", exception);
            return null;
        }
    }

    private record Bridge(
            Object driverSeat,
            Object passengerSeat,
            Method supportsSeat,
            Method tryMountWithOffset,
            Method tryDismount,
            Method tryTakeDriverSeat,
            Method setSeatOffset,
            Method occupant,
            Method seatCapacity) {
        private Object invoke(Method method, Object... arguments) {
            try {
                return method.invoke(null, arguments);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn("Ride On API call {} failed", method.getName(), exception);
                return null;
            }
        }

        private boolean invokeBoolean(Method method, Object... arguments) {
            return Boolean.TRUE.equals(invoke(method, arguments));
        }
    }
}
