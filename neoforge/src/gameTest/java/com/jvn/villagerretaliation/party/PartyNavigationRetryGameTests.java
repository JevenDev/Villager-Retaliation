package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.interaction.VillagerCommandController;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("party_commands")
@PrefixGameTestTemplate(false)
public final class PartyNavigationRetryGameTests {
    @GameTest(template = "empty")
    public static void failedFollowWaitsBeforeRetryingMovingOwner(GameTestHelper helper) {
        var fixture = new PartyCommandTransitionGameTests.Fixture(helper);
        var villager = new CountingVillager(helper.getLevel());
        villager.setPos(fixture.player.getX() + 8, fixture.player.getY(), fixture.player.getZ());
        VillagerCommandController.beginFollow(helper.getLevel(), villager, fixture.player.getUUID());
        try {
            for (int call = 0; call < 20; call++) {
                fixture.player.setPos(fixture.player.getX(), fixture.player.getY(), fixture.player.getZ() + 0.1D);
                VillagerCommandController.onVillagerTickPost(villager);
            }
            helper.assertValueEqual(villager.navigation().attempts, 2,
                    "failed follow tries coordinates and entity once despite owner movement");
        } catch (RuntimeException | Error failure) {
            VillagerCommandController.clear(villager);
            fixture.close();
            throw failure;
        }
        helper.runAfterDelay(11, () -> {
            try {
                VillagerCommandController.onVillagerTickPost(villager);
                helper.assertValueEqual(villager.navigation().attempts, 4, "follow retries after the cooldown");
                helper.succeed();
            } finally {
                VillagerCommandController.clear(villager);
                fixture.close();
            }
        });
    }

    @GameTest(template = "empty")
    public static void failedStayWaitsAndNewAnchorStartsImmediately(GameTestHelper helper) {
        var fixture = new PartyCommandTransitionGameTests.Fixture(helper);
        var villager = new CountingVillager(helper.getLevel());
        villager.setPos(fixture.player.getX() + 8, fixture.player.getY(), fixture.player.getZ());
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        BlockPos anchor = fixture.player.blockPosition();
        VillagerCommandController.beginStay(helper.getLevel(), villager, fixture.player.getUUID(), anchor);
        try {
            for (int call = 0; call < 20; call++) VillagerCommandController.onVillagerTickPost(villager);
            helper.assertValueEqual(villager.navigation().attempts, 1, "failed stay does not pathfind every tick");
            VillagerCommandController.beginStay(helper.getLevel(), villager, fixture.player.getUUID(), anchor.north());
            VillagerCommandController.onVillagerTickPost(villager);
            helper.assertValueEqual(villager.navigation().attempts, 2, "a new stay command bypasses the old cooldown");
        } catch (RuntimeException | Error failure) {
            VillagerCommandController.clear(villager);
            fixture.close();
            throw failure;
        }
        helper.runAfterDelay(11, () -> {
            try {
                VillagerCommandController.onVillagerTickPost(villager);
                helper.assertValueEqual(villager.navigation().attempts, 3, "stay retries after the cooldown");
                helper.succeed();
            } finally {
                VillagerCommandController.clear(villager);
                fixture.close();
            }
        });
    }

    private static final class CountingVillager extends Villager {
        CountingVillager(Level level) { super(EntityType.VILLAGER, level); }

        @Override
        protected PathNavigation createNavigation(Level level) { return new CountingNavigation(this, level); }

        CountingNavigation navigation() { return (CountingNavigation) getNavigation(); }
    }

    private static final class CountingNavigation extends GroundPathNavigation {
        private int attempts;

        CountingNavigation(Villager villager, Level level) { super(villager, level); }

        @Override
        public boolean moveTo(double x, double y, double z, double speed) {
            attempts++;
            return false;
        }

        @Override
        public boolean moveTo(Entity target, double speed) {
            attempts++;
            return false;
        }
    }
}
