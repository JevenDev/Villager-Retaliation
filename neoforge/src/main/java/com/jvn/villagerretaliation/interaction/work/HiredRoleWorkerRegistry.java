package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.npc.Villager;

public final class HiredRoleWorkerRegistry {
    private static final Map<HiredVillagerRole, HiredRoleWorker> WORKERS = new EnumMap<>(HiredVillagerRole.class);

    static {
        register(new LoggingWorker());
        register(new MiningWorker());
        register(new FarmingWorker());
        register(new NitwitWorker());
        register(new StatusOnlyWorker(HiredVillagerRole.BREWING, "Brewing automation is waiting for a configured brewing stand and supplies."));
        register(new StatusOnlyWorker(HiredVillagerRole.NAVIGATION, "Navigation work is ready for target discovery configuration."));
        register(new StatusOnlyWorker(HiredVillagerRole.ANIMAL_HANDLING, "Animal handling is waiting for lure supplies and a safe pen."));
        register(new StatusOnlyWorker(HiredVillagerRole.COMBAT, "Guard duty active. Combat is handled by existing retaliation systems."));
    }

    private HiredRoleWorkerRegistry() {
    }

    public static HiredRoleWorker get(HiredVillagerRole role) {
        return WORKERS.get(role);
    }

    public static void clearRuntimeState() {
        MiningWorker.clearRuntimeState();
    }

    public static void clearRuntimeState(Villager villager) {
        HiredPathMemory.releaseAll(villager);
        HiredPathMemory.clearNavigationProgress(villager);
    }

    private static void register(HiredRoleWorker worker) {
        WORKERS.put(worker.role(), worker);
    }
}
