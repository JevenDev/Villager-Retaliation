package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementManagementGameTests {
    private SettlementManagementGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void observedBlockersSurviveUnloadAndReloadWithoutReplayingWork(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        var area = area(town, SettlementAreaType.FARM, 0);
        town.putArea(fixture.leader().getUUID(), area);
        var resident = EntityType.VILLAGER.create(level);
        resident.setNoAi(true);
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, resident, town.id(), AllegianceAssignmentSource.ADMIN);
        town.assignDuty(resident.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, resident);
        town.removeWorker(resident.getUUID());
        var unseen = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
        helper.assertValueEqual(unseen.status(), "awaiting_worker_observation", "an assigned unobserved resident is not idle");
        var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(resident);
        state.putString("WorkerTaskState", "paused_missing_tool");
        state.putString("WorkerFailureReason", "missing_hoe");
        state.putString("Status", "interaction.work.farming.missing_hoe");
        SettlementBackgroundService.onUnloaded(resident);
        var worker = town.workers().get(resident.getUUID());
        helper.assertValueEqual(worker.status(), "workforce.missing_tools", "unloading retains the observed blocker");
        helper.assertTrue(worker.diagnostic().contains("hoe"), "the observed cause survives unloading");
        var saved = town.save(level.registryAccess());
        var restored = SettlementRecord.load(saved, level.registryAccess(), () -> {});
        var restoredWorker = restored.workers().get(resident.getUUID());
        helper.assertValueEqual(SettlementManagementService.residentWork(level, restored, resident.getUUID(), null, restoredWorker),
                new SettlementWorkStatus(worker.status(), worker.diagnostic()), "save reload preserves the same issue status");
        var before = restoredWorker.save();
        SettlementManagementService.residentWork(level, restored, resident.getUUID(), null, restoredWorker);
        helper.assertValueEqual(restoredWorker.save(), before, "status reads do not advance time, progress or receipts");
        var legacy = before.copy();
        legacy.remove("Diagnostic");
        helper.assertValueEqual(SettlementWorkerState.load(legacy).diagnostic(), "", "old snapshots need no migration");
        state.putString("WorkerTaskState", "returning_to_work_area");
        state.putString("Status", "interaction.work.status.returning_bounds");
        helper.assertValueEqual(SettlementManagementService.residentWork(level, town, resident.getUUID(), resident, worker).status(),
                "workforce.pathing", "a returned resident uses live state instead of the old blocker");
        SettlementBackgroundService.onUnloaded(resident);
        helper.assertValueEqual(worker.status(), "workforce.pathing", "a later unload replaces the old blocker");
        helper.assertValueEqual(worker.diagnostic(), "", "successful travel clears stale diagnostics");
        worker.status("missing_hoe");
        worker.status("resting");
        helper.assertValueEqual(worker.diagnostic(), "", "background rest carries no stale diagnostic");
        helper.assertValueEqual(worker.elapsed(level.getGameTime() + 100_000L), 200, "catch-up remains bounded to 200 ticks");
        helper.assertValueEqual(worker.elapsed(level.getGameTime() + 100_000L), 0, "the same interval cannot replay");
        helper.assertFalse(level.hasChunkAt(town.center()), "inspection leaves the remote settlement unloaded");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void remotePagesIncludeEveryResidentWithoutLoadingTheTown(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var expected = new HashSet<UUID>();
        for (int index = 0; index < 175; index++) {
            Villager resident = EntityType.VILLAGER.create(level);
            resident.setCustomName(Component.literal(String.format(java.util.Locale.ROOT, "Resident %03d", index)));
            resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
            VillagerSocialGraphSavedData.get(level).ensureProfile(level, resident);
            registry.addOrUpdateResident(fixture.town().id(), resident.getUUID(), true, level.getGameTime());
            expected.add(resident.getUUID());
        }
        long revision = fixture.town().revision();
        var found = new HashSet<UUID>();
        for (int index = 0; index < 8; index++) {
            var page = snapshot(fixture, Page.RESIDENTS, index, "");
            helper.assertValueEqual(page.total(), 175, "the census is not limited by packet capacity");
            helper.assertValueEqual(page.header().population(), 175, "older large settlements retain their population");
            helper.assertValueEqual(page.index(), index, "each requested page is returned");
            helper.assertFalse(page.openScreen(), "refreshes cannot reopen a closed screen");
            helper.assertTrue(page.entries().size() <= 24, "each packet has a bounded number of rows");
            for (var entry : page.entries()) {
                helper.assertTrue(found.add(entry.id()), "pages do not repeat residents");
                helper.assertValueEqual(((SettlementManagementPayload.Resident) entry).presence(), Presence.UNLOADED,
                        "cached residents are not presented as loaded");
            }
        }
        helper.assertValueEqual(found, expected, "all residents are reachable across pages");
        UUID selected = expected.iterator().next();
        helper.assertValueEqual(snapshot(fixture, Page.RESIDENTS, 0, selected.toString()).entries().getFirst().id(), selected,
                "world selections resolve the exact resident even when names are duplicated");
        var filtered = snapshot(fixture, Page.RESIDENTS, 1_000_000, "resident 17");
        helper.assertValueEqual(filtered.total(), 5, "search covers the entire roster");
        helper.assertValueEqual(filtered.index(), 0, "a stale page index is clamped after search");
        helper.assertValueEqual(filtered.entries().getFirst().name(), "Resident 170", "search keeps stable name order");
        helper.assertValueEqual(fixture.town().revision(), revision, "management reads do not mutate the town");
        helper.assertFalse(level.hasChunkAt(fixture.town().center()), "remote inspection does not load settlement chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void managementEnforcesDelegatedAccessAndLeadershipTransfer(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var delegate = player(helper, "TownDelegate");
        var request = new SettlementManagementRequestPayload(fixture.town().id().value(), Page.SUMMARY, 0, "", 7, false);
        helper.assertTrue(SettlementManagementService.snapshot(delegate, request) == null, "outsiders cannot read a town");
        var towns = SettlementManagementService.snapshot(delegate, new SettlementManagementRequestPayload(null, Page.TOWNS, 0, "", 8, false));
        helper.assertFalse(towns.entries().stream().anyMatch(entry -> entry.id().equals(fixture.town().id().value())),
                "the settlement list does not disclose an unauthorized town");
        helper.assertTrue(act(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.Permissions(
                delegate.getUUID(), Set.of(SettlementPermission.VIEW))), "the leader can grant view access");
        helper.assertTrue(SettlementManagementService.snapshot(delegate, request) != null, "view access permits reading");
        var area = area(fixture.town(), SettlementAreaType.HOME, 0);
        helper.assertFalse(act(delegate, fixture.town(), new SettlementManagementActionPayload.SaveArea(area)),
                "view access cannot create areas");
        helper.assertFalse(act(delegate, fixture.town(), new SettlementManagementActionPayload.Permissions(
                delegate.getUUID(), Set.of(SettlementPermission.BUILD))), "delegates cannot promote themselves");
        helper.assertFalse(act(delegate, fixture.town(), new SettlementManagementActionPayload.Transfer(delegate.getUUID())),
                "delegates cannot take leadership");
        helper.assertValueEqual(fixture.town().history().size(), 1, "rejected transfers do not add history");
        helper.assertTrue(act(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.Transfer(delegate.getUUID())),
                "explicit leadership transfer succeeds");
        helper.assertValueEqual(fixture.town().history().size(), 2, "the successful transfer adds one event");
        helper.assertValueEqual(fixture.town().history().getLast().type(), SettlementEvent.Type.LEADERSHIP_TRANSFERRED,
                "the transfer remains visible in the town history");
        helper.assertTrue(SettlementManagementService.snapshot(fixture.leader(), request) == null,
                "the former leader loses automatic access");
        helper.assertTrue(act(delegate, fixture.town(), new SettlementManagementActionPayload.SaveArea(area)),
                "the successor receives leadership permissions");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void managementRejectsInvalidAreaAndCrossTownEdits(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var builder = player(helper, "TownBuilder");
        fixture.town().setPermissions(fixture.leader().getUUID(), builder.getUUID(), Set.of(SettlementPermission.BUILD));
        var area = area(fixture.town(), SettlementAreaType.HOME, 0);
        helper.assertTrue(act(builder, fixture.town(), new SettlementManagementActionPayload.SaveArea(area)),
                "a building delegate can register a valid area");
        long revision = fixture.town().revision();
        var overlapping = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Overlap",
                area.min(), area.max(), 5, true);
        helper.assertFalse(act(builder, fixture.town(), new SettlementManagementActionPayload.SaveArea(overlapping)),
                "duplicate residential designations are rejected through the management action");
        helper.assertFalse(act(builder, fixture.town(), new SettlementManagementActionPayload.SaveArea(
                area(fixture.town(), SettlementAreaType.HOME, 200))), "stage bounds are enforced");
        helper.assertFalse(act(builder, fixture.town(), new SettlementManagementActionPayload.Budget(
                CurrencyAmount.ZERO, CurrencyAmount.of(100, 1))), "build permission does not grant treasury access");
        helper.assertValueEqual(fixture.town().revision(), revision, "rejected edits leave the settlement unchanged");
        helper.assertTrue(act(builder, fixture.town(), new SettlementManagementActionPayload.RemoveArea(area.id())),
                "a building delegate can remove the area");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void residentWorkShowsLiveBlockersWithoutStaleFailures(GameTestHelper helper) {
        var fixture = fixture(helper);
        var area = area(fixture.town(), SettlementAreaType.FARM, 0);
        fixture.town().putArea(fixture.leader().getUUID(), area);
        var level = helper.getLevel();
        var resident = EntityType.VILLAGER.create(level);
        resident.setNoAi(true);
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
        resident.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        level.addFreshEntity(resident);
        try {
            VillageAllegianceApi.assignKnown(level, resident, fixture.town().id(), AllegianceAssignmentSource.ADMIN);
            helper.assertTrue(act(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.Assign(resident.getUUID(), area.id())),
                    "the farmer takes a civic assignment");
            var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(resident);
            state.putString("WorkerTaskState", "paused_missing_tool");
            state.putString("WorkerFailureReason", "missing_hoe");
            state.putString("Status", "interaction.work.farming.missing_hoe");
            var blocked = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
            helper.assertValueEqual(blocked.status(), "workforce.missing_tools", "live failures replace the generic working label");
            helper.assertTrue(blocked.diagnostic().contains("hoe"), "the row explains the needed equipment");
            var worker = fixture.town().workers().get(resident.getUUID());
            worker.observe(level, resident, new SettlementWorkforceService.Duty(fixture.town(), area), level.getGameTime());
            helper.assertTrue(SettlementProgressionService.assess(level, fixture.town()).missing()
                    .contains(SettlementProgressionService.Requirement.FOOD_PRODUCTION), "missing equipment prevents a nonfunctional farm from counting toward advancement");
            state.putString("WorkerTaskState", "awaiting_instruction");
            state.putString("Status", "interaction.work.status.sleeping");
            var sleeping = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
            helper.assertValueEqual(sleeping.status(), "resting", "rest takes precedence over a retained failure");
            helper.assertTrue(sleeping.diagnostic().isEmpty(), "rest does not repeat a stale equipment warning");
            worker.observe(level, resident, new SettlementWorkforceService.Duty(fixture.town(), area), level.getGameTime());
            helper.assertFalse(worker.supportsService(), "rest does not erase an unresolved service setup problem");
            helper.assertFalse(SettlementWorkerState.load(worker.save()).supportsService(), "service readiness survives save loading");
            state.putString("WorkerTaskState", "returning_to_work_area");
            state.putString("Status", "interaction.work.status.returning_bounds");
            var walking = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
            helper.assertValueEqual(walking.status(), "workforce.pathing", "ordinary travel is shown as travel");
            helper.assertTrue(walking.diagnostic().isEmpty(), "old tool failures are hidden while travel succeeds");
            worker.observe(level, resident, new SettlementWorkforceService.Duty(fixture.town(), area), level.getGameTime());
            helper.assertTrue(worker.supportsService(), "resumed work restores service availability");
            worker.status("no_filter");
            worker.status("resting");
            helper.assertFalse(worker.supportsService(), "background rest also retains unresolved setup problems");
            worker.status("missing_materials");
            helper.assertTrue(worker.supportsService(), "a supply gap does not disable an otherwise configured service");
            worker.status("storage_full");
            helper.assertTrue(worker.supportsService(), "a filled stockpile does not disable a service");
            var legacy = worker.save();
            legacy.remove("ServiceBlocked");
            helper.assertTrue(SettlementWorkerState.load(legacy).supportsService(), "older worker records remain compatible");
            state.putString("WorkerTaskState", "waiting_for_materials");
            state.putString("Status", "interaction.work.hauling.no_demand");
            var idle = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
            helper.assertValueEqual(idle.status(), "no_demand", "an idle delivery schedule is not reported as blocked");
            helper.assertTrue(idle.diagnostic().isEmpty(), "idle service has no stale warning");
        } finally {
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void workPreviewsDistinguishIngredientsAndBlockedWorkstations(GameTestHelper helper) {
        var resident = EntityType.VILLAGER.create(helper.getLevel());
        var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(helper.getLevel(), resident,
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.COOK);
        var state = session.state();
        state.putString("WorkerTaskState", "waiting_for_materials");
        state.putString("WorkerFailureReason", "missing_cooking_crafting_materials");
        var ingredients = com.jvn.villagerretaliation.interaction.ClipboardWorkforceService.workPreview(session,
                com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime()));
        helper.assertValueEqual(ingredients.status(), com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus.MISSING_MATERIALS,
                "recipe ingredients have a distinct blocker");
        helper.assertTrue(ingredients.diagnostic().contains("recipe ingredients"), "the existing cooking explanation is reused");
        state.putString("WorkerTaskState", "failed_cooldown");
        state.putString("WorkerFailureReason", "cooking_crafting_table_unreachable");
        var workstation = com.jvn.villagerretaliation.interaction.ClipboardWorkforceService.workPreview(session,
                com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime()));
        helper.assertTrue(workstation.diagnostic().contains("cannot path to the crafting table"), "unreachable workstations explain the actual obstruction");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void staffingActionsRejectForeignResidentsAndRequireLeaderRelease(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var staff = player(helper, "TownStaff");
        fixture.town().setPermissions(fixture.leader().getUUID(), staff.getUUID(), Set.of(SettlementPermission.STAFF));
        var area = area(fixture.town(), SettlementAreaType.FARM, 0);
        fixture.town().putArea(fixture.leader().getUUID(), area);
        var level = helper.getLevel();
        Villager resident = EntityType.VILLAGER.create(level);
        resident.setNoAi(true);
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        resident.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        level.addFreshEntity(resident);
        try {
            helper.assertFalse(act(staff, fixture.town(), new SettlementManagementActionPayload.Assign(resident.getUUID(), area.id())),
                    "staff cannot silently enroll a foreign resident");
            VillageAllegianceApi.assignKnown(level, resident, fixture.town().id(), AllegianceAssignmentSource.ADMIN);
            helper.assertTrue(act(staff, fixture.town(), new SettlementManagementActionPayload.Assign(resident.getUUID(), area.id())),
                    "staff can assign an enrolled adult to a compatible job");
            helper.assertFalse(act(staff, fixture.town(), new SettlementManagementActionPayload.Inventory(resident.getUUID())),
                    "staff access cannot open equipment");
            fixture.leader().setPos(resident.position().add(1_000.0D, 0.0D, 0.0D));
            helper.assertFalse(act(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.Inventory(resident.getUUID())),
                    "remote management cannot transfer physical equipment from a distant worker");
            fixture.leader().setPos(resident.position());
            helper.assertTrue(act(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.Inventory(resident.getUUID())),
                    "nearby leaders can open the civic job inventory");
            var registry = VillageAllegianceRegistrySavedData.get(level);
            BlockPos otherCenter = fixture.town().center().offset(256, 0, 0);
            var otherId = registry.create(level.getGameTime(), level.dimension().location(), otherCenter, "Other town");
            var other = SettlementSavedData.get(level).establish(registry, otherId, fixture.leader().getUUID(), otherCenter, level.getGameTime()).orElseThrow();
            helper.assertFalse(act(fixture.leader(), other, new SettlementManagementActionPayload.Inventory(resident.getUUID())),
                    "equipment requests cannot cross settlement identities");
            helper.assertFalse(act(staff, fixture.town(), new SettlementManagementActionPayload.Release(resident.getUUID())),
                    "only the leader can release civic workers");
            helper.assertValueEqual(fixture.town().duties().get(resident.getUUID()), area.id(), "a rejected release preserves the assignment");
            helper.assertTrue(act(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.Release(resident.getUUID())),
                    "the leader can explicitly release a civic worker");
        } finally {
            fixture.leader().closeContainer();
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void depotTargetsUseTheServerHeldItemAndLogisticsPermission(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var area = area(fixture.town(), SettlementAreaType.DEPOT, 0);
        fixture.town().putArea(fixture.leader().getUUID(), area);
        var depot = fixture.town().registerDepot(fixture.leader().getUUID(), area.id(), area.min(), SettlementDepotPurpose.GENERAL);
        var delegate = player(helper, "TownHauler");
        fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
        ItemStack sample = new ItemStack(Items.BREAD, 3);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved bread"));
        delegate.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sample);
        try {
            var command = new SettlementManagementActionPayload.StockTarget(depot.id(), 24);
            helper.assertFalse(act(delegate, fixture.town(), command), "viewers cannot alter logistics");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
            helper.assertTrue(act(delegate, fixture.town(), command), "logistics delegates can set stock targets");
            helper.assertValueEqual(depot.stockTarget(sample), 24, "the server's held item supplies the exact component sample");
            helper.assertValueEqual(depot.stockTarget(new ItemStack(Items.BREAD)), -1, "the target does not match different item components");
            helper.assertValueEqual(sample.getCount(), 3, "configuring a target does not consume the held sample");
            helper.assertFalse(act(delegate, fixture.town(), new SettlementManagementActionPayload.StockTarget(depot.id(), 4_097)),
                    "unbounded targets are rejected");
            helper.assertFalse(act(delegate, fixture.town(), new SettlementManagementActionPayload.StockTarget(UUID.randomUUID(), 24)),
                    "foreign or missing depot IDs cannot be edited");
            helper.assertTrue(act(delegate, fixture.town(), new SettlementManagementActionPayload.StockTarget(depot.id(), 0)),
                    "zero removes the exact target");
            helper.assertTrue(depot.stockTargets().isEmpty(), "the removed target is no longer active");
        } finally {
            delegate.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void everyManagementPageAndActionRoundTripsWithExactCurrency(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var header = snapshot(fixture, Page.SUMMARY, 0, "").header();
        UUID id = UUID.randomUUID();
        var position = fixture.town().center();
        var fraction = CurrencyAmount.of(7, 13);
        var entries = List.<SettlementManagementPayload.Entry>of(
                new SettlementManagementPayload.Town(id, "Town", SettlementStage.CITY, 175, position),
                new SettlementManagementPayload.Resident(id, "Ada", ResourceLocation.withDefaultNamespace("farmer"), false,
                        Presence.UNLOADED, position, id, 13, 17.5F, "workforce.missing_tools", List.of(HiredVillagerRole.FARMING), null, null, null,
                        "Farmer needs a hoe before field work can continue."),
                new SettlementManagementPayload.Area(id, "Field", SettlementAreaType.FARM, position, position.above(), 7, true, 0, 1, 2),
                new SettlementManagementPayload.Depot(id, "Depot", id, position, SettlementDepotPurpose.FOOD, 6, 3, 130,
                        List.of(new SettlementManagementPayload.StockTarget("Bread", 64))),
                new SettlementManagementPayload.Applicant(id, "Bea", ResourceLocation.withDefaultNamespace("fisherman"), true),
                new SettlementManagementPayload.Order(id, "Bread", SettlementTradeOrder.Direction.BUY,
                        ResourceLocation.withDefaultNamespace("bread"), 64, fraction, CurrencyAmount.of(100, 1), false, "DISABLED"),
                new SettlementManagementPayload.Delegate(id, "Steward", Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF)));
        var pages = List.of(Page.TOWNS, Page.RESIDENTS, Page.AREAS, Page.DEPOTS, Page.APPLICANTS, Page.TRADE, Page.DELEGATES);
        roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, snapshot(fixture, Page.SUMMARY, 0, ""));
        for (int index = 0; index < entries.size(); index++) {
            Page page = pages.get(index);
            roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, new SettlementManagementPayload(
                    fixture.town().dimension(), page == Page.TOWNS ? null : header, page, 3, 175, "Bread", 23, false, List.of(entries.get(index))));
            roundTrip(helper, SettlementManagementRequestPayload.STREAM_CODEC,
                    new SettlementManagementRequestPayload(page == Page.TOWNS ? null : id, page, 3, "Bread", 23, false));
        }
        var commands = List.<SettlementManagementActionPayload.Command>of(
                new SettlementManagementActionPayload.Advance(),
                new SettlementManagementActionPayload.View(),
                new SettlementManagementActionPayload.Assign(id, id),
                new SettlementManagementActionPayload.Release(id),
                new SettlementManagementActionPayload.Applicant(id, true),
                new SettlementManagementActionPayload.SaveArea(area(fixture.town(), SettlementAreaType.HOME, 0)),
                new SettlementManagementActionPayload.RemoveArea(id),
                new SettlementManagementActionPayload.ConfigureDepot(id, SettlementDepotPurpose.MATERIALS, 7),
                new SettlementManagementActionPayload.StockTarget(id, 64),
                new SettlementManagementActionPayload.Treasury(false, 123),
                new SettlementManagementActionPayload.Budget(fraction, CurrencyAmount.of(100, 1)),
                new SettlementManagementActionPayload.SaveOrder(new SettlementTradeOrder(id, SettlementTradeOrder.Direction.SELL,
                        ResourceLocation.withDefaultNamespace("bread"), 64, fraction, fraction, false)),
                new SettlementManagementActionPayload.RemoveOrder(id),
                new SettlementManagementActionPayload.Permissions(id, Set.of(SettlementPermission.BUILD, SettlementPermission.LOGISTICS)),
                new SettlementManagementActionPayload.Transfer(id),
                new SettlementManagementActionPayload.Inventory(id));
        for (var command : commands) {
            roundTrip(helper, SettlementManagementActionPayload.STREAM_CODEC,
                    new SettlementManagementActionPayload(fixture.town().id().value(), command));
        }
        roundTrip(helper, SettlementManagementActionResultPayload.STREAM_CODEC,
                new SettlementManagementActionResultPayload(id, false));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void oversizedManagementPacketsAreRejectedBeforeRowsAreRead(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            buffer.writeResourceLocation(helper.getLevel().dimension().location());
            buffer.writeBoolean(false);
            buffer.writeEnum(Page.TOWNS);
            buffer.writeVarInt(0);
            buffer.writeVarInt(25);
            buffer.writeUtf("");
            buffer.writeVarInt(0);
            buffer.writeBoolean(true);
            buffer.writeVarInt(25);
            boolean rejected = false;
            try {
                SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            helper.assertTrue(rejected, "oversized page lengths are rejected before allocating or reading entries");
            rejected = false;
            try {
                new SettlementManagementRequestPayload(null, Page.TOWNS, 0, "x".repeat(65), 0, true);
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            helper.assertTrue(rejected, "search terms are bounded");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void historyIsBoundedPersistentAndCompatibleWithOlderSaves(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        helper.assertValueEqual(town.history().getFirst().type(), SettlementEvent.Type.ESTABLISHED,
                "enrollment records the settlement's first event");
        for (int index = 0; index < 260; index++) {
            town.recordEvent(index, SettlementEvent.Type.RESIDENT_JOINED, "Resident " + index);
        }
        helper.assertValueEqual(town.history().size(), SettlementRecord.MAX_HISTORY, "the history has a finite retention limit");
        helper.assertValueEqual(town.history().getFirst().subject(), "Resident 4", "only the oldest events expire");
        var saved = town.save(helper.getLevel().registryAccess());
        var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.history(), town.history(), "save loading preserves event identities, order and content");
        helper.assertValueEqual(restored.revision(), town.revision(), "loading does not replay history mutations");
        saved.remove("History");
        var legacy = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(legacy.history().isEmpty(), "older saves do not invent past activity");
        helper.assertValueEqual(legacy.leader(), town.leader(), "adding history preserves existing authority");
        var longName = SettlementEvent.create(0, SettlementEvent.Type.RESIDENT_JOINED, "a".repeat(127) + "\uD83C\uDF3E");
        helper.assertValueEqual(longName.subject(), "a".repeat(127), "bounded names do not split a Unicode character");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void historyPagesPreserveChronologyAndViewPermissions(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        for (int index = 0; index < 50; index++) {
            town.recordEvent(helper.getLevel().getGameTime(), SettlementEvent.Type.RESIDENT_JOINED, "Resident " + index);
        }
        long revision = town.revision();
        var first = snapshot(fixture, Page.HISTORY, 0, "");
        var second = snapshot(fixture, Page.HISTORY, 1, "");
        helper.assertValueEqual(first.total(), 51, "history pagination includes enrollment and arrivals");
        helper.assertValueEqual(first.entries().getFirst().name(), "Resident 49", "the latest event appears first even at the same tick");
        helper.assertValueEqual(second.entries().getFirst().name(), "Resident 25", "later pages continue chronological order");
        var selected = snapshot(fixture, Page.HISTORY, 0, "Resident 4");
        helper.assertValueEqual(selected.total(), 11, "search spans the complete retained history");
        helper.assertValueEqual(selected.entries().getFirst().name(), "Resident 49", "search preserves chronology");
        roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, first);
        var viewer = player(helper, "HistoryViewer");
        var request = new SettlementManagementRequestPayload(town.id().value(), Page.HISTORY, 0, "", 12, false);
        helper.assertTrue(SettlementManagementService.snapshot(viewer, request) == null, "outsiders cannot read settlement history");
        helper.assertValueEqual(town.revision(), revision, "reading history has no side effects");
        town.setPermissions(fixture.leader().getUUID(), viewer.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertTrue(SettlementManagementService.snapshot(viewer, request) != null, "delegated view permission includes history");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "history does not load the settlement");
        helper.succeed();
    }

    private static <T> void roundTrip(GameTestHelper helper, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            codec.encode(buffer, value);
            helper.assertValueEqual(codec.decode(buffer), value, "management packet retains all fields");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the entire packet is consumed");
        } finally {
            buffer.release();
        }
    }

    private static SettlementManagementPayload snapshot(Fixture fixture, Page page, int index, String query) {
        return SettlementManagementService.snapshot(fixture.leader(),
                new SettlementManagementRequestPayload(fixture.town().id().value(), page, index, query, 7, false));
    }

    private static boolean act(ServerPlayer player, SettlementRecord town, SettlementManagementActionPayload.Command command) {
        return SettlementManagementService.execute(player, new SettlementManagementActionPayload(town.id().value(), command));
    }

    private static SettlementArea area(SettlementRecord town, SettlementAreaType type, int offset) {
        BlockPos min = town.center().offset(offset, 0, 0);
        return new SettlementArea(UUID.randomUUID(), type, "Management area", min, min.offset(3, 2, 3), 5, true);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos center = new BlockPos(2_000_000 + origin.getX() * 8, 90, 2_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var leader = player(helper, "TownManager");
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Management");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        helper.assertFalse(level.hasChunkAt(center), "the town starts unloaded");
        return new Fixture(leader, town);
    }

    private record Fixture(ServerPlayer leader, SettlementRecord town) {
    }
}
