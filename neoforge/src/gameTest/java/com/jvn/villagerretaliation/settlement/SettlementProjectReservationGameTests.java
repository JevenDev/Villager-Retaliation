package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementProjectReservationGameTests {
    private SettlementProjectReservationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void cancelledPendingWorkRejectsOverlappingActiveSavesInEitherOrder(GameTestHelper helper) {
        funded(helper, SettlementProject.Status.CANCELLED, fixture -> {
            var replacement = replacement(fixture, fixture.origin());
            assertOverlapRejected(helper, fixture, replacement);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void completedPendingWorkRejectsOverlappingActiveSavesInEitherOrder(GameTestHelper helper) {
        funded(helper, SettlementProject.Status.COMPLETED, fixture -> {
            var replacement = replacement(fixture, fixture.origin());
            assertOverlapRejected(helper, fixture, replacement);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void twoFinishedPendingProjectsCannotReserveTheSameSavedFootprint(GameTestHelper helper) {
        funded(helper, SettlementProject.Status.CANCELLED, fixture -> {
            var replacement = replacement(fixture, fixture.origin());
            replacement.observeSite(fixture.area());
            fixture.site().sections().values().forEach(section -> replacement.site().record(
                    SettlementTerrainSection.load("ConstructionSurvey", replacement.id(), section.save())));
            replacement.site().queue(new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(
                    fixture.origin(), Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), null))));
            replacement.finish(SettlementProject.Status.COMPLETED);
            var isolated = load(helper, projects(helper, fixture, replacement));
            helper.assertTrue(isolated.projects().get(replacement.id()).site().pending(),
                    "the second project is individually valid and retains its world change");
            assertOverlapRejected(helper, fixture, replacement);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void pendingWorkAllowsASeparateProjectAndBothSaveOrders(GameTestHelper helper) {
        funded(helper, SettlementProject.Status.CANCELLED, fixture -> {
            var replacement = replacement(fixture, fixture.origin().north(3));
            helper.assertTrue(fixture.town().startProject(fixture.town().leader(), replacement),
                    "a retired project's pending footprint does not consume an active slot or block a separate site");
            for (boolean reversed : List.of(false, true)) {
                var restored = load(helper, reversed ? projects(helper, fixture, replacement, fixture.project())
                        : projects(helper, fixture, fixture.project(), replacement));
                helper.assertTrue(restored.projects().get(fixture.project().id()).site().pending(),
                        "separate pending work survives either save order");
                helper.assertValueEqual(restored.activeProject(fixture.builder().getUUID()).id(), replacement.id(),
                        "the builder retains the separate active project");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void cancelledPendingWorkSurvivesRemovalOfItsFormerDesignation(GameTestHelper helper) {
        funded(helper, SettlementProject.Status.CANCELLED, fixture -> {
            helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), fixture.area().id()),
                    "retiring a designation preserves its already performed work");
            var restored = load(helper, fixture.town().save(helper.getLevel().registryAccess()));
            var project = restored.projects().get(fixture.project().id());
            helper.assertTrue(project.site().pending() && project.reservesFootprint(),
                    "the paid operation remains reserved without requiring a retired duty");
            helper.assertValueEqual(project.placed(), 1, "the existing completed placement is retained");
            helper.assertValueEqual(restored.depots().get(fixture.depot().id()).count(stack -> stack.is(Items.OAK_PLANKS)), 0,
                    "loading cannot refund the material spent on pending work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void deliveredWorkReleasesItsFootprintAndRetainsOverlappingHistory(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, blocks(), fixture -> {
            var level = helper.getLevel();
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            fixture.site().queue(new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(
                    fixture.origin(), Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), null))));
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.CANCELLED);
            var replacement = replacement(fixture, fixture.origin());
            helper.assertFalse(fixture.town().startProject(fixture.town().leader(), replacement),
                    "the footprint stays reserved while its world operation is pending");
            SettlementConstructionSiteService.update(level, fixture.town());
            helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.OAK_PLANKS), "delivery places the reserved block");
            helper.assertFalse(fixture.project().reservesFootprint(), "actual world delivery releases the retired footprint");
            helper.assertTrue(fixture.town().startProject(fixture.town().leader(), replacement),
                    "the builder can now reuse the delivered site");
            for (boolean reversed : List.of(false, true)) {
                var restored = load(helper, reversed ? projects(helper, fixture, replacement, fixture.project())
                        : projects(helper, fixture, fixture.project(), replacement));
                helper.assertValueEqual(restored.projects().size(), 2, "history and a reused footprint survive either save order");
                helper.assertFalse(restored.projects().get(fixture.project().id()).reservesFootprint(),
                        "retired history does not recreate a reservation");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_project_reservations")
    public static void oldFinishedHistoryWithoutTerrainObservationsCanShareAnActiveFootprint(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.COMPLETED);
            var history = fixture.project().save();
            history.remove("Site");
            var replacement = replacement(fixture, fixture.origin());
            helper.assertTrue(fixture.town().startProject(fixture.town().leader(), replacement),
                    "an empty old observation does not reserve completed history");
            var previous = SettlementProject.load(history);
            for (boolean reversed : List.of(false, true)) {
                var restored = load(helper, reversed ? projects(helper, fixture, replacement, previous)
                        : projects(helper, fixture, previous, replacement));
                helper.assertTrue(restored.projects().get(previous.id()).site() == null,
                        "older saves retain their absent terrain observations");
                helper.assertValueEqual(restored.activeProject(fixture.builder().getUUID()).id(), replacement.id(),
                        "an older completed project cannot block the current project");
            }
        });
    }

    private static void funded(GameTestHelper helper, SettlementProject.Status status,
                               Consumer<SettlementBackgroundConstructionGameTests.Fixture> check) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, status == SettlementProject.Status.COMPLETED ? 2 : 1));
            SettlementBackgroundConstructionGameTests.work(helper, fixture, 20);
            if (status == SettlementProject.Status.CANCELLED) fixture.town().finishProject(fixture.project().id(), status);
            helper.assertValueEqual(fixture.project().status(), status, "the fixture reaches its requested terminal state");
            helper.assertTrue(fixture.site().pending(), "actual paid construction is awaiting world delivery");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "the original site remains unloaded");
            check.accept(fixture);
        });
    }

    private static SettlementProject replacement(SettlementBackgroundConstructionGameTests.Fixture fixture, BlockPos origin) {
        return SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(),
                fixture.plan(), origin, UUID.randomUUID(), 0L);
    }

    private static void assertOverlapRejected(GameTestHelper helper,
                                             SettlementBackgroundConstructionGameTests.Fixture fixture,
                                             SettlementProject replacement) {
        helper.assertValueEqual(load(helper, projects(helper, fixture, fixture.project())).projects().size(), 1,
                "the original reserved work is individually valid");
        helper.assertValueEqual(load(helper, projects(helper, fixture, replacement)).projects().size(), 1,
                "the replacement is individually valid");
        rejectsOverlap(helper, projects(helper, fixture, fixture.project(), replacement));
        rejectsOverlap(helper, projects(helper, fixture, replacement, fixture.project()));
    }

    private static CompoundTag projects(GameTestHelper helper, SettlementBackgroundConstructionGameTests.Fixture fixture,
                                        SettlementProject... projects) {
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        var entries = new ListTag();
        for (var project : projects) entries.add(project.save());
        saved.put("Projects", entries);
        return saved;
    }

    private static SettlementRecord load(GameTestHelper helper, CompoundTag saved) {
        return SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
    }

    private static void rejectsOverlap(GameTestHelper helper, CompoundTag saved) {
        try {
            load(helper, saved);
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage().contains("overlaps reserved work"),
                    "the save fails because the physical reservations overlap");
            return;
        }
        helper.fail("overlapping paid construction reservations must not load");
    }

    private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks() {
        return Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState());
    }
}
