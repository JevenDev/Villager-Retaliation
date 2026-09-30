package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
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
    public static void issuesExcludeNormalWorkAndLinkCurrentBlockers(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        var area = area(town, SettlementAreaType.FARM, 0);
        town.putArea(fixture.leader().getUUID(), area);
        var worker = issueWorker(helper, fixture, area, "River", "missing_hoe");
        var page = snapshot(fixture, Page.ISSUES, 0, worker.id().toString());
        helper.assertValueEqual(page.total(), 1, "a missing tool produces one worker issue");
        var issue = (SettlementManagementPayload.Issue) page.entries().getFirst();
        helper.assertValueEqual(issue.kind(), SettlementManagementPayload.IssueKind.WORKER, "the cause is classified as worker trouble");
        helper.assertValueEqual(issue.cause(), "missing_hoe", "the actual missing tool is retained");
        helper.assertValueEqual(issue.target(), Page.RESIDENTS, "the issue links to residents");
        helper.assertValueEqual(issue.targetId(), worker.id(), "navigation identifies the resident exactly");
        helper.assertValueEqual(issue.area(), area.id(), "quick controls carry the observed assignment");
        roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, page);
        roundTrip(helper, SettlementManagementRequestPayload.STREAM_CODEC,
                new SettlementManagementRequestPayload(town.id().value(), Page.ISSUES, 0, "", 3, false));
        for (String status : List.of("resting", "workforce.pathing", "workforce.depositing", "no_demand", "paused",
                "waiting_for_crops", "waiting_for_bite", "workforce.no_targets", "no_construction_project")) {
            worker.status(status);
            helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 0, worker.id().toString()).total(), 0,
                    "ordinary waiting or paused work is not an issue: " + status);
        }
        for (String status : List.of("no_filter", "no_furnace", "profession_required", "storage_full", "missing_materials",
                "construction_conflict", "workforce.missing_tools", "workforce.material_storage_unreachable",
                "workforce.inventory_full", "needs_attention", "unsafe", "awaiting_worker_observation", "no_observed_field",
                "no_observed_fishery", "no_observed_forest", "no_observed_mine", "no_observed_hunting_target",
                "construction_needs_observation", "excavation_needs_observation", "tree_needs_observation",
                "tree_needs_native_growth", "animal_unreachable", "animal_drop_unreachable", "hunting_visibility_unobserved",
                "hunting_projectile_needs_world", "hunting_combat_unavailable", "hunting_loot_unreachable",
                "mining_support_unreachable", "hazard_unreachable", "excavation_blocked")) {
            worker.status(status);
            var current = (SettlementManagementPayload.Issue) snapshot(fixture, Page.ISSUES, 0, worker.id().toString()).entries().getFirst();
            helper.assertValueEqual(current.cause(), status, "the latest blocker replaces the old cause");
            helper.assertTrue(current.needsAttention(), "the browser can derive attention from the same issue");
            helper.assertFalse(current.diagnostic().recovery().isBlank(), "every actionable issue has recovery guidance");
            helper.assertValueEqual(current.id(), issue.id(), "refresh preserves the selected worker issue");
        }
        var saved = town.save(level.registryAccess());
        var restored = SettlementRecord.load(saved, level.registryAccess(), () -> {});
        helper.assertValueEqual(SettlementIssueService.issues(level, restored, SettlementProgressionService.assess(level, restored)),
                SettlementIssueService.issues(level, town, SettlementProgressionService.assess(level, town)), "reload reconstructs the same issues");
        helper.assertTrue(act(fixture.leader(), town, new SettlementManagementActionPayload.WorkerEnabled(worker.id(), area.id(), false)),
                "the issue's quick pause is accepted");
        helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 0, worker.id().toString()).total(), 0, "a deliberate pause hides the old blocker");
        helper.assertTrue(act(fixture.leader(), town, new SettlementManagementActionPayload.WorkerEnabled(worker.id(), area.id(), true)),
                "the resident page can resume the same worker");
        town.putArea(fixture.leader().getUUID(), new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), area.priority(), false));
        helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 0, worker.id().toString()).total(), 0, "a paused area does not repeat old work failures");
        helper.assertFalse(level.hasChunkAt(town.center()), "issue inspection and controls do not load the town");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_management")
    public static void issuePagesAreBoundedSearchableAndPermissionChecked(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var area = area(town, SettlementAreaType.FARM, 0);
        town.putArea(fixture.leader().getUUID(), area);
        for (int index = 0; index < 30; index++) issueWorker(helper, fixture, area, "Worker " + index, "missing_hoe");
        var before = town.save(helper.getLevel().registryAccess());
        var first = snapshot(fixture, Page.ISSUES, 0, "");
        var second = snapshot(fixture, Page.ISSUES, 1, "");
        helper.assertValueEqual(first.entries().size(), SettlementManagementRequestPayload.PAGE_SIZE, "issues obey the existing packet limit");
        helper.assertTrue(second.total() > 30, "progression shortages appear alongside worker issues");
        helper.assertValueEqual(snapshot(fixture, Page.SUMMARY, 0, "").header().issues(), 31,
                "summary counts thirty worker issues and one shared food shortage without treating progression as a failure");
        var food = snapshot(fixture, Page.DEMAND, 0, "").entries().stream().map(SettlementManagementPayload.Demand.class::cast)
                .filter(demand -> demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
        helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 0, food.id().toString()).total(), 1,
                "all residents share one actionable nutrition reserve");
        helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 99, "missing_hoe").total(), 30, "search matches causes across the complete town");
        helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 99, "missing_hoe").index(), 1, "stale page indices are clamped");
        var seen = new HashSet<UUID>();
        for (var page : List.of(first, second)) {
            roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, page);
            for (var entry : page.entries()) helper.assertTrue(seen.add(entry.id()), "issue pages have no duplicates");
        }
        helper.assertValueEqual(seen.size(), first.total(), "all issues remain reachable");
        for (var entry : first.entries()) {
            if (entry instanceof SettlementManagementPayload.Issue issue && issue.kind() == SettlementManagementPayload.IssueKind.PROGRESSION) {
                Page target = switch (issue.cause()) {
                    case "population" -> Page.RESIDENTS;
                    case "depot", "food_stock" -> Page.DEPOTS;
                    default -> Page.AREAS;
                };
                helper.assertValueEqual(issue.target(), target, "progression shortages link to their management page");
            }
        }
        helper.assertValueEqual(town.save(helper.getLevel().registryAccess()), before, "issue reads do not change time, stock or saved work");
        var outsider = player(helper, "IssueViewer");
        var request = new SettlementManagementRequestPayload(town.id().value(), Page.ISSUES, 0, "", 4, false);
        helper.assertTrue(SettlementManagementService.snapshot(outsider, request) == null, "outsiders cannot inspect issues");
        town.setPermissions(fixture.leader().getUUID(), outsider.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertTrue(SettlementManagementService.snapshot(outsider, request) != null, "view delegates can inspect issues");
        var issue = (SettlementManagementPayload.Issue) snapshot(fixture, Page.ISSUES, 0, "missing_hoe").entries().getFirst();
        helper.assertFalse(act(outsider, town, new SettlementManagementActionPayload.WorkerEnabled(issue.resident(), issue.area(), false)),
                "issue visibility does not grant worker controls");
        boolean rejected = false;
        try {
            new SettlementManagementPayload.Issue(issue.id(), issue.name(), issue.kind(), "x".repeat(129), "", issue.target(),
                    issue.targetId(), issue.resident(), issue.area(), true);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "issue causes are bounded");
        rejected = false;
        try {
            new SettlementManagementPayload(first.dimension(), first.header(), Page.ISSUES, 0, 25, "", 1, false,
                    java.util.Collections.nCopies(25, issue));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "oversized issue pages are rejected");
        helper.succeed();
    }

    private static SettlementWorkerState issueWorker(GameTestHelper helper, Fixture fixture, SettlementArea area, String name, String status) {
        var resident = EntityType.VILLAGER.create(helper.getLevel());
        resident.setNoAi(true);
        resident.setCustomName(Component.literal(name));
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(helper.getLevel(), resident, fixture.town().id(), AllegianceAssignmentSource.ADMIN);
        VillagerSocialGraphSavedData.get(helper.getLevel()).ensureProfile(helper.getLevel(), resident);
        fixture.town().assignDuty(resident.getUUID(), area.id());
        SettlementWorkforceService.reconcile(helper.getLevel(), resident);
        SettlementBackgroundService.onUnloaded(resident);
        var worker = fixture.town().workers().get(resident.getUUID());
        worker.status(status);
        return worker;
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
        state.putString("WorkerTaskState", "moving_to_target");
        state.putString("WorkerFailureReason", "farming_profession_required");
        helper.assertValueEqual(SettlementManagementService.residentWork(level, town, resident.getUUID(), resident, worker).status(),
                "workforce.pathing", "travel overrides retained setup failures");
        state.putString("WorkerTaskState", "moving_to_storage");
        helper.assertValueEqual(SettlementManagementService.residentWork(level, town, resident.getUUID(), resident, worker).status(),
                "workforce.depositing", "resupply travel is normal activity");
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
        helper.assertValueEqual(fixture.town().history().size(), 1, "an offer does not record a completed transfer");
        helper.assertTrue(SettlementOwnershipService.accept(delegate, fixture.town(), fixture.town().leadershipOffer().id()),
                "the successor accepts the management offer");
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
    public static void unloadedResidentDiagnosticsExplainBlockersAndHideOrdinaryWaiting(GameTestHelper helper) {
        var fixture = fixture(helper);
        var area = area(fixture.town(), SettlementAreaType.FARM, 0);
        fixture.town().putArea(fixture.leader().getUUID(), area);
        var resident = EntityType.VILLAGER.create(helper.getLevel());
        resident.setPos(area.center().getCenter());
        VillageAllegianceApi.assignKnown(helper.getLevel(), resident, fixture.town().id(), AllegianceAssignmentSource.ADMIN);
        fixture.town().assignDuty(resident.getUUID(), area.id());
        SettlementWorkforceService.reconcile(helper.getLevel(), resident);
        SettlementBackgroundService.onUnloaded(resident);
        var worker = fixture.town().workers().get(resident.getUUID());
        for (String status : java.util.List.of("missing_hoe", "missing_rod", "missing_axe", "missing_pickaxe",
                "missing_materials", "no_filter", "no_crafting_table", "no_furnace", "no_brewing_stand",
                "choose_brewing_order", "storage_full", "supply_full", "output_full", "no_safe_mining_route",
                "mine_conflict", "construction_needs_observation", "hunting_storage_unreachable",
                "missing_breeding_food", "awaiting_worker_observation", "missing_ladders", "missing_backing",
                "fill_supply_full", "mining_support_unreachable", "hazard_unreachable", "excavation_blocked",
                "tree_needs_native_growth", "sapling_blocked", "no_observed_hunting_target")) {
            worker.status(status);
            var row = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
            helper.assertValueEqual(row.presence(), Presence.UNLOADED, "the resident has no loaded entity");
            helper.assertValueEqual(row.status(), status, "the existing status remains available");
            helper.assertFalse(row.diagnostic().isBlank(), "the unloaded blocker explains a corrective action: " + status);
        }
        for (String status : java.util.List.of("resting", "active", "idle", "no_demand", "no_construction_project",
                "waiting_for_crops", "waiting_for_bite", "waiting_for_furnace", "waiting_for_brewing_stand",
                "waiting_for_ore", "waiting_for_trees", "no_animal_products",
                "crafted", "delivered", "caught_fish", "built_block", "equipped_hoe", "unknown_future_status")) {
            worker.status(status);
            var row = (SettlementManagementPayload.Resident) snapshot(fixture, Page.RESIDENTS, 0, resident.getUUID().toString()).entries().getFirst();
            helper.assertTrue(row.diagnostic().isEmpty(), "ordinary waiting and successful work have no stale warning: " + status);
        }
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
    public static void diagnosticFactsKeepPersonalAndCivicRecoveryAdviceSeparate(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = EntityType.VILLAGER.create(level);
        var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(level, villager,
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER);
        var state = session.state();
        state.putString("WorkerTaskState", "awaiting_instruction");
        for (String failure : List.of("transfer_storage_changed", "transfer_worker_changed", "transfer_contract_changed")) {
            state.putString("WorkerFailureReason", failure);
            var brain = com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, level.getGameTime());
            var facts = com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(session, brain);
            var personal = com.jvn.villagerretaliation.interaction.ClipboardWorkforceService.workPreview(session, brain);
            String civic = SettlementWorkerDiagnostics.describe(facts);
            helper.assertValueEqual(personal.status(), facts.status(), "both views use the same factual classification");
            helper.assertFalse(personal.diagnostic().contains("worksite") || personal.diagnostic().contains("depot"),
                    "personal recovery never requires civic administration");
            helper.assertTrue(personal.diagnostic().contains(failure.equals("transfer_storage_changed") ? "assigned" : "contract"),
                    "personal recovery identifies the personal storage or contract");
            helper.assertFalse(civic.contains("personal contract"), "civic recovery never directs the leader to personal hiring");
            helper.assertFalse(civic.isBlank(), "civic transfers retain their recovery advice");
        }
        session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(level, villager,
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.COOK);
        state.putString("WorkerTaskState", "waiting_for_materials");
        state.putString("WorkerFailureReason", "missing_cooking_crafting_materials");
        var brain = com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, level.getGameTime());
        var facts = com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(session, brain);
        helper.assertTrue(SettlementWorkerDiagnostics.describe(facts).contains("depot"), "civic supplies advice uses depot stock");
        helper.assertTrue(com.jvn.villagerretaliation.interaction.PersonalWorkerDiagnostics.preview(session, brain, facts).contains("assigned"),
                "interaction reports use the same personal assigned-storage advice");
        session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(level, villager,
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
        state.putString("WorkerTaskState", "failed_cooldown");
        state.putString("WorkerFailureReason", "missing_builder_materials_storage_unreachable");
        brain = com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, level.getGameTime());
        facts = com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(session, brain);
        helper.assertTrue(facts.materialStorageUnreachable() && !facts.missingMaterials(), "known stock behind a blocked path is not a material shortage");
        helper.assertTrue(SettlementWorkerDiagnostics.describe(facts).contains("route"), "civic advice preserves the factual access blocker");
        state.putString("WorkerTaskState", "moving_to_target");
        brain = com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, level.getGameTime());
        facts = com.jvn.villagerretaliation.interaction.HiredWorkerFacts.observe(session, brain);
        helper.assertTrue(SettlementWorkerDiagnostics.describe(facts).isBlank()
                && com.jvn.villagerretaliation.interaction.PersonalWorkerDiagnostics.preview(session, brain, facts).isBlank(),
                "successful travel hides stale blockers in both views");
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

    @GameTest(template = "empty", batch = "settlement_management")
    public static void managementFailuresIdentifyPermissionStaleStateAndMissingRequirements(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var leader = fixture.leader();
        var viewer = player(helper, "FailureViewer");
        town.setPermissions(leader.getUUID(), viewer.getUUID(), Set.of(SettlementPermission.VIEW));
        var missing = UUID.randomUUID();
        helper.assertValueEqual(failure(viewer, town, new SettlementManagementActionPayload.AutomaticStaffing(null)),
                "permission_staff", "view permission cannot restore staffing");
        helper.assertValueEqual(failure(viewer, town, new SettlementManagementActionPayload.Management(false)),
                "leader_required", "town policy requires current leadership");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.Release(missing)),
                "duty_changed", "stale release reports the missing duty");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.AutomaticStaffing(missing)),
                "resident_missing", "unknown residents are identified");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.Treasury(true, 10)),
                "player_funds", "deposit reports actual missing currency");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.Treasury(false, 10)),
                "treasury_funds", "withdrawal reports the treasury shortage");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.RemoveArea(missing)),
                "area_missing", "stale areas are identified");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.RemoveOrder(missing)),
                "order_missing", "stale orders are identified");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.CancelProject(missing)),
                "project_missing", "stale projects are identified");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.Inventory(missing)),
                "resident_unloaded", "inventory explains that the resident must be loaded");
        helper.assertValueEqual(failure(leader, town, new SettlementManagementActionPayload.Advance()),
                "advancement_requirements", "advancement explains unmet requirements");
        var request = new SettlementManagementActionPayload(town.id().value(), new SettlementManagementActionPayload.AutomaticStaffing(null));
        roundTrip(helper, SettlementManagementActionPayload.STREAM_CODEC, request);
        var response = new SettlementManagementActionResultPayload(town.id().value(), false, request.request(), "permission_staff");
        roundTrip(helper, SettlementManagementActionResultPayload.STREAM_CODEC, response);
        helper.assertFalse(request.request().equals(new SettlementManagementActionPayload(town.id().value(), request.command()).request()),
                "distinct attempts receive distinct correlation identities");
        helper.assertValueEqual(town.economy().balance(), CurrencyAmount.ZERO, "rejected financial actions do not move currency");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_management")
    public static void tradeAndResidentCareIssuesUseEvidenceAndExcludeOrdinaryWaiting(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        var farm = area(town, SettlementAreaType.FARM, 0);
        town.putArea(town.leader(), farm);
        var worker = issueWorker(helper, fixture, farm, "Care resident", "waiting_for_crops");
        var resident = EntityType.VILLAGER.create(level);
        resident.setUUID(worker.id());
        resident.setPos(farm.center().getCenter());
        var state = SettlementResidentState.capture(resident, town, level.getGameTime()).save();
        state.putInt("Food", 0);
        state.putFloat("Health", 4);
        state.putLong("ShortageTicks", 2_400L);
        town.recordResident(SettlementResidentState.load(state));
        var care = snapshot(fixture, Page.ISSUES, 0, worker.id().toString());
        helper.assertValueEqual(care.total(), 2, "persistent hunger and low health are visible without a worker failure");
        helper.assertTrue(care.entries().stream().map(SettlementManagementPayload.Issue.class::cast).allMatch(
                issue -> issue.kind() == SettlementManagementPayload.IssueKind.RESIDENT && issue.needsAttention()),
                "resident problems link to the resident and attention filter");
        var hunger = care.entries().stream().map(SettlementManagementPayload.Issue.class::cast)
                .filter(issue -> issue.cause().equals("resident_hungry")).findFirst().orElseThrow();
        helper.assertValueEqual(hunger.target(), Page.DEMAND, "hunger opens the shared food requirement");
        var food = snapshot(fixture, Page.DEMAND, 0, "").entries().stream().map(SettlementManagementPayload.Demand.class::cast)
                .filter(demand -> demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
        helper.assertValueEqual(hunger.targetId(), food.id(), "hunger navigation retains the semantic requirement identity");
        helper.assertValueEqual(hunger.resident(), worker.id(), "hunger still identifies the affected resident");
        roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, care);
        state.putInt("Food", 20);
        state.putFloat("Health", 20);
        town.recordResident(SettlementResidentState.load(state));
        helper.assertValueEqual(snapshot(fixture, Page.ISSUES, 0, worker.id().toString()).total(), 0, "recovered residents stop reporting old problems");
        UUID orderId = UUID.randomUUID();
        var order = new SettlementTradeOrder(orderId, SettlementTradeOrder.Direction.BUY,
                ResourceLocation.withDefaultNamespace("bread"), 64, CurrencyAmount.of(4, 1), CurrencyAmount.of(64, 1), true);
        town.economy().putOrder(order);
        for (var status : SettlementTradeService.Status.values()) {
            town.economy().recordResults(java.util.Map.of(orderId, SettlementTradeService.Result.blocked(status)));
            int expected = switch (status) {
                case STAGE_LOCKED, NO_STAFF, UNAVAILABLE, PRICE_LIMIT, NO_SPACE, BUDGET_LIMIT -> 1;
                default -> 0;
            };
            var page = snapshot(fixture, Page.ISSUES, 0, orderId.toString());
            helper.assertValueEqual(page.total(), expected, "trade status classification: " + status);
            if (expected > 0) roundTrip(helper, SettlementManagementPayload.STREAM_CODEC, page);
        }
        helper.assertFalse(level.hasChunkAt(town.center()), "diagnostics do not load the remote town");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_management")
    public static void persistentProblemNotificationsDeduplicateAndRespectCooldown(GameTestHelper helper) {
        var tracker = new SettlementNotifications.Problems();
        var problem = Set.of("worker:missing_hoe");
        helper.assertValueEqual(tracker.update(problem, 0), 0, "one transient observation stays quiet");
        helper.assertValueEqual(tracker.update(problem, 200), 1, "a persistent new problem is reported once");
        helper.assertValueEqual(tracker.update(problem, 1_600), 0, "unchanged problems never repeat");
        helper.assertValueEqual(tracker.update(Set.of("worker:missing_rod"), 1_800), 0, "a changed cause must persist");
        helper.assertValueEqual(tracker.update(Set.of("worker:missing_rod"), 2_000), 1, "a new persistent cause can be reported");
        tracker.update(Set.of(), 2_200);
        helper.assertValueEqual(tracker.update(problem, 2_400), 0, "reappearing problems wait for confirmation");
        helper.assertValueEqual(tracker.update(problem, 2_600), 0, "cooldown prevents bursts");
        helper.assertValueEqual(tracker.update(problem, 3_200), 1, "a recurring persistent problem reports after cooldown");
        helper.assertValueEqual(SettlementWorkerDiagnostics.diagnostic("simulation_capped").severity(),
                SettlementWorkerDiagnostics.Severity.INFO, "normal cap waiting is informational");
        helper.assertValueEqual(SettlementWorkerDiagnostics.diagnostic("construction_needs_observation").severity(),
                SettlementWorkerDiagnostics.Severity.OBSERVATION, "observation needs are distinct from errors");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_management")
    public static void simulationDiagnosticsExplainDisabledCappedAndSuspendedTowns(GameTestHelper helper) {
        var town = fixture(helper).town();
        var level = helper.getLevel();
        var cap = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES;
        int original = cap.get();
        try {
            cap.set(0);
            helper.assertValueEqual(SettlementSimulationService.suspension(level, town), "simulation_disabled",
                    "disabled background work has a distinct explanation");
            var issues = SettlementIssueService.issues(level, town, SettlementProgressionService.assess(level, town));
            helper.assertTrue(issues.stream().anyMatch(issue -> issue.kind() == SettlementManagementPayload.IssueKind.SIMULATION
                    && issue.cause().equals("simulation_disabled") && !issue.needsAttention()), "disabled simulation is informational");
            cap.set(1);
            var towns = SettlementSavedData.get(level).settlements().stream().filter(value -> value.territory().active())
                    .filter(value -> !SettlementSimulationService.foreground(level, value)).toList();
            var selected = SettlementSimulationService.select(towns, Math.floorDiv(level.getServer().overworld().getGameTime(), 24_000L), 1);
            for (var candidate : towns) {
                String expected = selected.contains(candidate) ? "" : "simulation_capped";
                String actual = SettlementSimulationService.suspension(level, candidate);
                helper.assertTrue(actual.equals(expected) || selected.contains(candidate) && actual.equals("simulation_queued"),
                        "diagnostics distinguish eligibility from a pending work queue");
            }
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(SettlementSimulationService.suspension(level, restored),
                    SettlementSimulationService.suspension(level, town), "reload preserves simulation identity");
            var inactive = new SettlementRecord(VillageAllegianceId.random(), level.dimension().location(), BlockPos.ZERO,
                    town.leader(), 0L, () -> {});
            inactive.migrateTerritory(Set.of(), "ownership_conflict");
            helper.assertValueEqual(SettlementSimulationService.suspension(level, inactive), "simulation_suspended",
                    "inactive territory has a distinct explanation");
        } finally {
            cap.set(original);
        }
        helper.succeed();
    }

    private static String failure(ServerPlayer player, SettlementRecord town, SettlementManagementActionPayload.Command command) {
        return SettlementManagementService.executeFailure(player, new SettlementManagementActionPayload(town.id().value(), command));
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
