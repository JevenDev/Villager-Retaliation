package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.npc.Villager;

public final class HiredRoleWorkerRegistry {
    private static final Map<HiredVillagerRole, HiredRoleWorker> WORKERS = new EnumMap<>(HiredVillagerRole.class);

    static {
        register(new CombatWorker());
        register(new LoggingWorker());
        register(new MiningWorker());
        register(new FarmingWorker());
        register(new FishingWorker());
        register(new NitwitWorker());
        register(new BrewingWorker());
        register(new BuilderWorker());
        register(new AnimalBreedingWorker());
    }

    private HiredRoleWorkerRegistry() {
    }

    public static HiredRoleWorker get(HiredVillagerRole role) {
        return WORKERS.get(role);
    }

    public static void clearRuntimeState() {
        AbstractBlockWorker.clearSharedRuntimeState();
    }

    public static void clearRuntimeState(Villager villager) {
        HiredPathMemory.clear(villager);
    }

    private static void register(HiredRoleWorker worker) {
        WORKERS.put(worker.role(), worker);
    }
}
