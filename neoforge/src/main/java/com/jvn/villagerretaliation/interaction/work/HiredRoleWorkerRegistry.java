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
        register(new StatusOnlyWorker(HiredVillagerRole.BREWING, "I am ready to brew once there is a proper stand and the needed supplies."));
        register(new StatusOnlyWorker(HiredVillagerRole.NAVIGATION, "I am ready to guide the way once you settle on where I should lead."));
        register(new StatusOnlyWorker(HiredVillagerRole.ANIMAL_HANDLING, "I am ready to tend the animals once there are lures and a safe pen."));
        register(new StatusOnlyWorker(HiredVillagerRole.COMBAT, "I remain on guard and ready to answer trouble."));
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
