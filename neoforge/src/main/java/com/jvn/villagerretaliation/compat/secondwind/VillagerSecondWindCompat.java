package com.jvn.villagerretaliation.compat.secondwind;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Optional, class-linkage-safe bridge to Second Wind's public common API. */
public final class VillagerSecondWindCompat {
    public static final ResourceLocation ADAPTER_ID = VillagerRetaliation.id("villager");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Method notifyStateChanged;
    private static Method resolvePresentationPose;
    private static boolean active;

    private VillagerSecondWindCompat() {
    }

    public static void init() {
        if (!ModList.get().isLoaded("secondwind")) return;
        try {
            Class<?> api = Class.forName("com.jvn.secondwind.api.SecondWindApi");
            Class<?> adapterType = Class.forName("com.jvn.secondwind.api.ExternalDownedEntityAdapter");
            Class<?> reviveControlType;
            try {
                reviveControlType = Class.forName("com.jvn.secondwind.api.ExternalReviveControl");
            } catch (ClassNotFoundException ignored) {
                reviveControlType = null;
            }
            Class<?>[] adapterInterfaces = reviveControlType == null
                    ? new Class<?>[]{adapterType}
                    : new Class<?>[]{adapterType, reviveControlType};
            Object adapter = Proxy.newProxyInstance(adapterType.getClassLoader(), adapterInterfaces, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "Villager Retaliation Second Wind adapter";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                LivingEntity entity = null;
                if (args != null) {
                    for (Object argument : args) {
                        if (argument instanceof LivingEntity living) {
                            entity = living;
                            break;
                        }
                    }
                }
                return switch (method.getName()) {
                    case "isDowned" -> entity instanceof Villager villager && VillagerDownedService.isDowned(villager);
                    case "canRevive" -> entity instanceof Villager villager && VillagerDownedService.isDowned(villager);
                    case "revive" -> revive(entity);
                    case "reviveHealthOverride" -> entity instanceof Villager villager
                            ? VillagerDownedService.recoveryHealth(villager)
                            : OptionalDouble.empty();
                    case "applyConfiguredRegeneration" -> false;
                    default -> throw new UnsupportedOperationException("Unknown Second Wind adapter method " + method.getName());
                };
            });
            api.getMethod("registerExternalAdapter", ResourceLocation.class, adapterType).invoke(null, ADAPTER_ID, adapter);
            notifyStateChanged = api.getMethod("notifyExternalStateChanged", LivingEntity.class);
            resolvePresentationPose = api.getMethod("resolvePresentationPose", LivingEntity.class);
            active = true;
            LOGGER.info("Enabled Villager Retaliation integration with Second Wind.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Second Wind is installed but its entity compatibility API could not be initialized.", exception);
        }
    }

    public static void notifyStateChanged(Villager villager) {
        if (!active || notifyStateChanged == null) return;
        try {
            notifyStateChanged.invoke(null, villager);
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Failed to synchronize a VR villager with Second Wind.", exception);
        }
    }

    public static Optional<ResourceLocation> resolvePose(Villager villager) {
        if (!active || resolvePresentationPose == null) return Optional.empty();
        try {
            Object result = resolvePresentationPose.invoke(null, villager);
            if (result instanceof Optional<?> optional && optional.orElse(null) instanceof ResourceLocation pose) {
                return Optional.of(pose);
            }
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Failed to resolve a Second Wind villager pose.", exception);
        }
        return Optional.empty();
    }

    public static boolean isActive() {
        return active;
    }

    private static boolean revive(LivingEntity entity) {
        if (!(entity instanceof Villager villager) || !VillagerDownedService.isDowned(villager)) return false;
        VillagerDownedService.recover(villager);
        return !VillagerDownedService.isDowned(villager);
    }
}
