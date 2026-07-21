package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Map;
import net.minecraft.world.entity.npc.Villager;

public final class HiredRoleWorkerRegistry {
    private static final Map<HiredVillagerRole, HiredRoleWorker> WORKERS = Map.ofEntries(
            worker(new CombatWorker()),
            worker(new HuntingWorker()),
            worker(new LoggingWorker()),
            worker(new MiningWorker()),
            worker(new FarmingWorker()),
            worker(new FishingWorker()),
            worker(new NitwitWorker()),
            worker(new BrewingWorker()),
            worker(new CraftsmanWorker()),
            worker(new BuilderWorker()),
            worker(new AnimalBreedingWorker()),
            worker(new CookingWorker()),
            worker(new SmeltingWorker()),
            worker(new CourierWorker()));

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

    private static Map.Entry<HiredVillagerRole, HiredRoleWorker> worker(HiredRoleWorker worker) {
        if (worker == null || worker.role() == null) {
            throw new IllegalArgumentException("Hired workers must declare a role");
        }
        return Map.entry(worker.role(), worker);
    }
}
