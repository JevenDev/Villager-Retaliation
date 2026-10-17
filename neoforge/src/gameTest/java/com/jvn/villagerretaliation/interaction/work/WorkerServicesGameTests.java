package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_structure")
@PrefixGameTestTemplate(false)
public final class WorkerServicesGameTests {
    @GameTest(template = "empty")
    public static void workersUseSuppliedPoliciesAndResources(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = EntityType.VILLAGER.create(level);
        var position = helper.absolutePos(new BlockPos(1, 2, 1));
        villager.setPos(position.getCenter());
        var services = new ProbeServices();
        var context = new HiredWorkContext(HiredJobInventory.getJobInventory(villager), new CompoundTag(),
                position, position, position, 4, 4, true, 50, 100, 100, 100, false, false, null, services);
        var worker = new SmeltingWorker();
        var blocked = worker.supplyQuota(level, villager, context);
        helper.assertValueEqual(blocked.status(), "interaction.work.supply.test_policy", "the worker uses its supplied gathering policy");
        helper.assertValueEqual(services.released, HiredVillagerRole.SMELTER, "pausing releases the supplied production resources");
        services.allowed = true;
        helper.assertTrue(worker.supplyQuota(level, villager, context) == null, "changed policy is observed without rebuilding the context");
        worker.tick(level, villager, null, context);
        helper.assertValueEqual(services.productionCalls, 2, "both role dispatch and furnace execution use the supplied resource access");
        var guard = new CombatWorker().tick(level, villager, null, context);
        helper.assertValueEqual(guard.status(), "test_equipment", "guard equipment policy is supplied through the same boundary");
        helper.succeed();
    }

    private static final class ProbeServices implements HiredWorkServices {
        private boolean allowed;
        private int productionCalls;
        private HiredVillagerRole released;

        @Override
        public GatheringDecision gathering() {
            return new GatheringDecision(true, this.allowed, "test_policy");
        }

        @Override
        public HiredProductionService.Access production(HiredVillagerRole role) {
            this.productionCalls++;
            return new HiredProductionService.Access(true, null, "test_production");
        }

        @Override
        public void releaseProduction(HiredVillagerRole role) {
            this.released = role;
        }

        @Override
        public boolean maintainGuard(HiredWorkContext context) {
            throw new AssertionError("Equipment must be resolved before guard movement");
        }

        @Override
        public boolean hasGuardOrder() {
            throw new AssertionError("Equipment must be resolved before target search");
        }

        @Override
        public boolean allowsGuardTarget(BlockPos position) {
            throw new AssertionError("Equipment must be resolved before target search");
        }

        @Override
        public WorkResult equipGuard(HiredWorkContext context, double speed) {
            return WorkResult.idle("test_equipment");
        }
    }
}
