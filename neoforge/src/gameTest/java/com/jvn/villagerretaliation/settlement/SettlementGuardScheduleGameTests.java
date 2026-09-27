package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementGuardScheduleGameTests {
    private SettlementGuardScheduleGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guard_schedule")
    public static void complementaryShiftsCoverEveryTickAndLeaveDaylightForBothGuards(GameTestHelper helper) {
        var day = new UUID(0L, 0L);
        var night = new UUID(0L, 1L);
        int dayFamily = 0;
        int nightFamily = 0;
        int dayDuty = 0;
        int nightDuty = 0;
        for (long time = 0; time < 24_000L; time++) {
            boolean first = SettlementWorkforceService.workingHours(time, day, HiredVillagerRole.COMBAT);
            boolean second = SettlementWorkforceService.workingHours(time, night, HiredVillagerRole.COMBAT);
            helper.assertTrue(first != second, "exactly one complementary guard shift covers every time of day");
            if (first) dayDuty++;
            if (second) nightDuty++;
            if (time >= 10L && time < 12_000L) {
                if (!first) dayFamily++;
                if (!second && (time < 2_000L || time >= 9_000L)) nightFamily++;
            }
        }
        helper.assertValueEqual(dayDuty, 12_000, "day guards retain half a day of duty");
        helper.assertValueEqual(nightDuty, 12_000, "night guards retain half a day of duty");
        helper.assertTrue(dayFamily >= 1_900 && nightFamily >= 3_000, "both shifts include time for native daytime family behavior");
        helper.assertTrue(SettlementWorkforceService.workingHours(-1L, night, HiredVillagerRole.COMBAT),
                "the night shift remains correct before a day boundary");
        helper.assertFalse(SettlementWorkforceService.workingHours(24_001L, day, HiredVillagerRole.COMBAT),
                "the dawn family window repeats on later days");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guard_schedule")
    public static void aDayGuardCanCompleteTheRealNativeCourtshipAtDawn(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        long previous = helper.getLevel().getDayTime();
        try {
            var level = helper.getLevel();
            level.setDayTime(1_000L);
            var guard = fixture.guard;
            var partner = fixture.partner;
            HiredVillagerWorkService.onVillagerTickPost(guard);
            helper.assertFalse(SettlementWorkforceService.onDuty(level, guard), "the day guard has a dawn break");
            helper.assertFalse(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, guard),
                    "civic work yields the actual native brain during family time");
            guard.getBrain().setMemory(MemoryModuleType.BREED_TARGET, partner);
            guard.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(guard, List.of(partner)));
            var behavior = new VillagerMakeLove();
            long now = level.getGameTime();
            helper.assertTrue(behavior.tryStart(level, guard, now), "the actual native villager breeding behavior starts for a civic guard");
            for (int tick = 1; tick <= 350; tick++) behavior.tickOrStop(level, guard, now + tick);
            helper.assertValueEqual(guard.getAge(), 6_000, "the native birth completes and applies its parent cooldown");
            helper.assertValueEqual(SettlementPopulationService.familyFood(guard), 0, "native courtship consumes the guard's actual provisions");
            helper.assertValueEqual(VillageAllegianceRegistrySavedData.get(level).residentRoster(fixture.setup.town().id(), now).size(), 3,
                    "the family has one actual new child");
        } finally {
            cleanup(helper, fixture);
            helper.getLevel().setDayTime(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guard_schedule")
    public static void dayGuardCanHaveABackgroundFamilyDuringItsDawnBreak(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        long previous = helper.getLevel().getDayTime();
        try {
            fixture.guard.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.partner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.getLevel().setDayTime(1_000L);
            finishFamily(helper, fixture);
            helper.assertValueEqual(fixture.setup.town().arrivals().size(), 1, "an unloaded day guard has a dawn family window");
        } finally {
            cleanup(helper, fixture);
            helper.getLevel().setDayTime(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guard_schedule")
    public static void nightGuardRestIsRespectedBeforeItsEveningBackgroundFamilyWindow(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        long previous = helper.getLevel().getDayTime();
        try {
            fixture.guard.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.partner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.getLevel().setDayTime(6_000L);
            finishFamily(helper, fixture);
            helper.assertTrue(fixture.setup.town().courtships().isEmpty(), "the background honors the night guard's daytime rest");
            helper.assertTrue(fixture.setup.town().arrivals().isEmpty(), "sleeping time cannot be spent on background courtship");
            helper.getLevel().setDayTime(10_000L);
            finishFamily(helper, fixture);
            helper.assertValueEqual(fixture.setup.town().arrivals().size(), 1, "the night guard can form a family after its daytime rest");
        } finally {
            cleanup(helper, fixture);
            helper.getLevel().setDayTime(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guard_schedule")
    public static void pausingOrReleasingANightGuardRestoresItsOriginalSchedule(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        try {
            var guard = fixture.guard;
            var custom = new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).changeActivityAt(5_000, Activity.MEET).build();
            guard.getBrain().setSchedule(custom);
            SettlementWorkforceService.reconcile(helper.getLevel(), guard);
            var wrapped = guard.getBrain().getSchedule();
            helper.assertTrue(wrapped != custom, "civic night duty installs its own daytime rest window");
            helper.assertValueEqual(wrapped.getActivityAt(6_000), Activity.REST, "night duty replaces morning work with rest");
            helper.assertValueEqual(wrapped.getActivityAt(10_000), Activity.MEET, "other activities retain the original schedule");
            var settings = SettlementWorkerConfigurationService.snapshot(helper.getLevel(), fixture.setup.town(), guard.getUUID());
            var options = new java.util.EnumMap<>(settings.options());
            options.put(SettlementWorkerSetting.ENABLED, 0);
            helper.assertTrue(SettlementWorkerConfigurationService.configure(fixture.setup.leader(), fixture.setup.town(), guard.getUUID(),
                    fixture.post.id(), new SettlementWorkerSettings(HiredVillagerRole.COMBAT, options, java.util.Set.of(), java.util.Set.of())),
                    "the leader can pause this civic guard");
            helper.assertTrue(guard.getBrain().getSchedule() == custom, "pausing restores the exact preexisting schedule");
            options.put(SettlementWorkerSetting.ENABLED, 1);
            SettlementWorkerConfigurationService.configure(fixture.setup.leader(), fixture.setup.town(), guard.getUUID(), fixture.post.id(),
                    new SettlementWorkerSettings(HiredVillagerRole.COMBAT, options, java.util.Set.of(), java.util.Set.of()));
            helper.assertValueEqual(guard.getBrain().getSchedule().getActivityAt(6_000), Activity.REST, "resuming restores night duty rest");
            helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.setup.leader(), fixture.setup.town().id(), guard.getUUID()),
                    "the leader can end civic guard duty");
            helper.assertTrue(guard.getBrain().getSchedule() == custom, "release restores the original schedule without replacing it with a default");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guard_schedule")
    public static void nativeReloadAndBrainRefreshReinstallTheNightGuardSchedule(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        try {
            var original = fixture.guard;
            var saved = original.saveWithoutId(new CompoundTag());
            original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored = EntityType.VILLAGER.create(helper.getLevel());
            restored.load(saved);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "the actual native night guard reloads");
            try {
                helper.assertValueEqual(restored.getBrain().getSchedule().getActivityAt(6_000), Activity.REST,
                        "native join reinstalls the saved duty's rest schedule");
                restored.refreshBrain(helper.getLevel());
                SettlementBackgroundService.onActive(restored);
                helper.assertValueEqual(restored.getBrain().getSchedule().getActivityAt(6_000), Activity.REST,
                        "a rebuilt native brain regains the same civic rest schedule");
                fixture.setup.town().removeDuty(restored.getUUID());
                SettlementWorkforceService.reconcile(helper.getLevel(), restored);
                helper.assertTrue(restored.getBrain().getSchedule() == Schedule.VILLAGER_DEFAULT,
                        "removing the saved duty releases the replacement schedule");
            } finally {
                restored.discard();
            }
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "isolated_settlement_night_guard_rest")
    public static void nightGuardActuallySleepsInItsAssignedBedAndWakesForDuty(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        long previous = level.getDayTime();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {
            }

            @Override
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                   net.minecraft.gametest.framework.GameTestRunner runner) {
                cleanup(helper, fixture);
                level.setDayTime(previous);
            }

            @Override
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                   net.minecraft.gametest.framework.GameTestRunner runner) {
                cleanup(helper, fixture);
                level.setDayTime(previous);
            }

            @Override
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo previous,
                                         net.minecraft.gametest.framework.GameTestInfo next,
                                         net.minecraft.gametest.framework.GameTestRunner runner) {
            }
        });
        level.setDayTime(6_000L);
        var guard = fixture.guard;
        var bed = fixture.setup.town().center().offset(1, 0, 2);
        var home = fixture.setup.home();
        for (var position : BlockPos.betweenClosed(home.min().offset(-1, -2, -1),
                home.max().offset(1, 0, 1).atY(home.min().getY() - 2))) {
            level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(SettlementHousingService.assign(fixture.setup.leader(), fixture.setup.town(), guard.getUUID(), bed),
                "the night guard receives a real individual bed");
        guard.setPos(bed.west().getBottomCenter());
        guard.setOnGround(true);
        guard.setNoAi(false);
        helper.runAtTickTime(160L, () -> {
            helper.assertFalse(SettlementWorkforceService.onDuty(level, guard), "the morning belongs to the guard's rest period");
            helper.assertTrue(guard.getBrain().isActive(Activity.REST), "native schedule updates select the morning rest activity");
            helper.assertTrue(guard.isSleeping(), "the native sleep behavior enters the assigned bed during daytime: position=" + guard.position()
                    + ", home=" + guard.getBrain().getMemory(MemoryModuleType.HOME) + ", bed=" + level.getBlockState(bed)
                    + ", slept=" + guard.getBrain().getMemory(MemoryModuleType.LAST_SLEPT)
                    + ", woke=" + guard.getBrain().getMemory(MemoryModuleType.LAST_WOKEN) + ", now=" + level.getGameTime());
            helper.assertValueEqual(guard.getSleepingPos().orElseThrow(), bed, "native sleep uses the player's assigned bed");
            helper.assertFalse(VillagerBehaviorSuppressionPolicy.suppresses(guard, VillagerBehaviorSuppressionPolicy.Behavior.SLEEPING),
                    "off-duty civic guards permit native sleep");
            level.setDayTime(14_000L);
        });
        helper.runAtTickTime(180L, () -> {
            helper.assertTrue(SettlementWorkforceService.onDuty(level, guard), "the evening begins the guard's working shift");
            helper.assertFalse(guard.isSleeping(), "native guard work wakes the resident when duty resumes");
            helper.assertTrue(HiredVillagerFocusService.isOnDutyGuard(level, guard), "the actual guard focus resumes after daytime sleep");
            helper.succeed();
        });
    }

    private static void finishFamily(GameTestHelper helper, Fixture fixture) {
        long now = helper.getLevel().getGameTime();
        for (int interval = 0; interval <= 5; interval++) SettlementFamilyService.update(helper.getLevel(), fixture.setup.town(), now + interval * 200L);
    }

    private static Fixture fixture(GameTestHelper helper, boolean night) {
        var setup = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        var center = setup.town().center();
        var head = center.offset(5, 0, 2);
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
        SettlementBuildingService.refresh(level, setup.town(), setup.home());
        var post = new SettlementArea(UUID.randomUUID(), SettlementAreaType.GUARD_POST, "Guard post",
                center.offset(-1, 0, -1), center.offset(2, 2, 1), 5, true);
        setup.town().putArea(setup.leader().getUUID(), post);
        var guard = EntityType.VILLAGER.create(level);
        guard.setUUID(new UUID(UUID.randomUUID().getMostSignificantBits(), UUID.randomUUID().getLeastSignificantBits() & ~1L | (night ? 1L : 0L)));
        guard.setPos(center.getBottomCenter());
        guard.setOnGround(true);
        guard.setNoAi(true);
        guard.setVillagerData(guard.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
        VillagerPresetNameRegistry.setStoredGender(guard, VillagerGender.MALE);
        guard.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        VillageAllegianceApi.assignKnown(level, guard, setup.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(level.addFreshEntity(guard), "the guard is present");
        helper.assertTrue(SettlementWorkforceService.assign(level, setup.leader(), setup.town().id(), guard, post.id()),
                "the resident receives actual civic guard duty");
        var partner = EntityType.VILLAGER.create(level);
        partner.setPos(center.east().getBottomCenter());
        partner.setOnGround(true);
        partner.setNoAi(true);
        VillagerPresetNameRegistry.setStoredGender(partner, VillagerGender.FEMALE);
        partner.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        VillageAllegianceApi.assignKnown(level, partner, setup.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(level.addFreshEntity(partner), "the other parent is present");
        SettlementCareService.onUnloaded(partner);
        return new Fixture(setup, post, guard, partner);
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        for (var member : VillageAllegianceRegistrySavedData.get(helper.getLevel()).residentRoster(fixture.setup.town().id(), helper.getLevel().getGameTime())) {
            var entity = helper.getLevel().getEntity(member.id());
            if (entity != null) entity.discard();
        }
        if (!fixture.guard.isRemoved()) fixture.guard.discard();
        if (!fixture.partner.isRemoved()) fixture.partner.discard();
    }

    private record Fixture(SettlementPopulationGameTests.Fixture setup, SettlementArea post, Villager guard, Villager partner) {
    }
}
