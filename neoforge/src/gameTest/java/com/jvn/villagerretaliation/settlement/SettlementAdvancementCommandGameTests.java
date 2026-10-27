package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_advancement")
@PrefixGameTestTemplate(false)
public final class SettlementAdvancementCommandGameTests {
    private SettlementAdvancementCommandGameTests() {
    }

    @GameTest(template = "empty")
    public static void localCommandSkipsOnlyWaitAndPersistsOneTier(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        helper.assertTrue(SettlementProgressionService.assess(helper.getLevel(), fixture.town()).missing().isEmpty(),
                "the fixture meets every non-time requirement");
        helper.assertFalse(SettlementProgressionService.advance(fixture.leader(), fixture.town().id()),
                "ordinary advancement still requires sustained support");
        helper.assertValueEqual(execute(helper, fixture.source(), ""), 1, "the local command skips the wait");
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.HAMLET, "only the next tier unlocks");
        helper.assertValueEqual(fixture.town().support().sustainedTicks(), 0L, "advancement resets support");
        helper.assertValueEqual(execute(helper, fixture.source(), ""), 0, "another tier requires its own requirements");
        helper.assertValueEqual(fixture.town().history().stream()
                .filter(event -> event.type() == SettlementEvent.Type.STAGE_ADVANCED).count(), 1L,
                "only a successful advancement records an event");
        var restored = SettlementSavedData.load(SettlementSavedData.get(helper.getLevel())
                .save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess())
                .get(fixture.town().id()).orElseThrow();
        helper.assertValueEqual(restored.stage(), SettlementStage.HAMLET, "the new tier survives reload");
        helper.assertValueEqual(restored.history(), fixture.town().history(), "the advancement history survives reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAdvancementStillSucceedsAfterSustainedSupport(GameTestHelper helper) {
        var fixture = fixture(helper);
        for (long tick = 0L; tick <= SettlementProgressionService.requiredTicks(); tick += 200L) {
            fixture.town().support().observe(tick, true, false);
        }
        helper.assertTrue(SettlementProgressionService.advance(fixture.leader(), fixture.town().id()),
                "ordinary leader advancement succeeds after the configured waiting period");
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.HAMLET, "ordinary advancement unlocks the next tier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingPopulationFoodDepotAndServiceStillBlock(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        UUID resident = fixture.residents().getLast();
        registry.removeResident(fixture.town().id(), resident);
        assertBlocked(helper, fixture, SettlementProgressionService.Requirement.POPULATION);
        registry.addOrUpdateResident(fixture.town().id(), resident, true, helper.getLevel().getGameTime());

        fixture.depot().clearContent();
        assertBlocked(helper, fixture, SettlementProgressionService.Requirement.FOOD_STOCK);
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 64));

        fixture.town().removeDuty(fixture.residents().getFirst());
        assertBlocked(helper, fixture, SettlementProgressionService.Requirement.FOOD_PRODUCTION);
        fixture.town().assignDuty(fixture.residents().getFirst(), fixture.farm().id());

        fixture.town().removeDepot(fixture.depot().id());
        assertBlocked(helper, fixture, SettlementProgressionService.Requirement.DEPOT);
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.CAMP, "refused commands never advance");
        helper.assertTrue(fixture.town().history().stream()
                .noneMatch(event -> event.type() == SettlementEvent.Type.STAGE_ADVANCED),
                "refused commands never record advancement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandRefreshesHousingBeforeAdvancement(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        helper.getLevel().removeBlock(fixture.beds().getFirst(), false);
        assertBlocked(helper, fixture, SettlementProgressionService.Requirement.HOUSING);
        placeBed(helper, fixture.beds().getFirst());
        helper.assertValueEqual(execute(helper, fixture.source(), ""), 1, "repaired housing permits advancement immediately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitTargetSupportsConsoleAndRequiresOperator(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        var unprivileged = fixture.source().withPermission(0);
        helper.assertFalse(SettlementProgressionService.advanceImmediately(unprivileged, fixture.town().id()),
                "the service rejects non-operators");
        boolean denied = false;
        try {
            execute(helper, unprivileged, fixture.town().id().value().toString());
        } catch (CommandSyntaxException expected) {
            denied = true;
        }
        helper.assertTrue(denied, "the command tree rejects non-operators");
        var console = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        helper.assertValueEqual(execute(helper, console, UUID.randomUUID().toString()), 0, "unknown targets are refused");
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertFalse(SettlementProgressionService.advanceImmediately(console.withLevel(nether), fixture.town().id()),
                "advancement must use the settlement dimension");
        helper.assertValueEqual(execute(helper, console, fixture.town().id().value().toString()), 1,
                "an operator console can advance an explicit target without being its leader");
        helper.assertValueEqual(fixture.town().leader(), fixture.leader().getUUID(), "ownership is preserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highestTierCannotAdvanceAgain(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        while (fixture.town().stage().next().isPresent()) fixture.town().advance();
        long revision = fixture.town().revision();
        helper.assertValueEqual(execute(helper, fixture.source(), ""), 0, "the highest tier is refused");
        helper.assertFalse(SettlementProgressionService.advanceImmediately(fixture.source(), fixture.town().id()),
                "the service also refuses the highest tier");
        helper.assertValueEqual(fixture.town().revision(), revision, "no rewards or state changes repeat at the highest tier");
        helper.succeed();
    }

    private static void assertBlocked(GameTestHelper helper, Fixture fixture,
                                      SettlementProgressionService.Requirement requirement) throws Exception {
        helper.assertValueEqual(execute(helper, fixture.source(), ""), 0, "missing " + requirement + " blocks advancement");
        helper.assertTrue(SettlementProgressionService.assess(helper.getLevel(), fixture.town()).missing().contains(requirement),
                "the assessment reports missing " + requirement);
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.CAMP, "a failed check preserves the tier");
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String target) throws CommandSyntaxException {
        return helper.getLevel().getServer().getCommands().getDispatcher()
                .execute("vr admin settlement advance" + (target.isEmpty() ? "" : " " + target), source);
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TownSteward"));
        leader.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var data = SettlementSavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = data.establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm",
                center, center.offset(5, 2, 5), 5, true);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                center.above(3), center.offset(5, 6, 5), 5, true);
        data.putArea(registry, id, leader.getUUID(), farm);
        data.putArea(registry, id, leader.getUUID(), home);
        var depot = town.registerDepot(leader.getUUID(), farm.id(), center.offset(1, 0, 1), SettlementDepotPurpose.GENERAL);
        SettlementTestPlots.activateDepot(level, town, depot);
        depot.observeRules(List.of());
        var residents = new ArrayList<UUID>();
        var beds = new ArrayList<BlockPos>();
        for (int index = 0; index < SettlementStage.HAMLET.population(); index++) {
            UUID resident = UUID.randomUUID();
            residents.add(resident);
            registry.addOrUpdateResident(id, resident, true, level.getGameTime());
            if (index == 0) town.assignDuty(resident, farm.id());
            beds.add(home.min().offset((index % 3) * 2, 0, (index / 3) * 2 + 1));
        }
        for (BlockPos pos : BlockPos.betweenClosed(home.min().below(), home.max().atY(home.min().getY() - 1))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        beds.forEach(head -> placeBed(helper, head));
        SettlementHousingLivabilityGameTests.furnish(level, home, home.min().getY());
        SettlementBuildingService.refresh(level, town, home);
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        return new Fixture(town, farm, depot, leader, residents, beds);
    }

    private static void placeBed(GameTestHelper helper, BlockPos head) {
        var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
        helper.getLevel().setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 2);
        helper.getLevel().getPoiManager().add(head, helper.getLevel().registryAccess()
                .registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
    }

    private record Fixture(SettlementRecord town, SettlementArea farm, SettlementDepot depot, ServerPlayer leader,
                           List<UUID> residents, List<BlockPos> beds) {
        CommandSourceStack source() {
            return leader.createCommandSourceStack().withPermission(2);
        }
    }
}
