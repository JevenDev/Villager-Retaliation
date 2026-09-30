package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementStorageGameTests {
    private SettlementStorageGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void ordinaryStorageInheritsOverridesAndSurvivesReconciliation(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var first = fixture.worker();
        var second = worker(helper, fixture);
        var shared = links(fixture.container(), SettlementStorageLinks.Purpose.GENERAL, 4);
        var personal = links(fixture.container().north(), SettlementStorageLinks.Purpose.OUTPUT, 1);
        helper.getLevel().setBlockAndUpdate(fixture.container().north(), Blocks.CHEST.defaultBlockState());
        try {
            helper.assertValueEqual(save(fixture, null, shared), "", "worksites accept ordinary barrels");
            helper.assertValueEqual(SettlementStorageService.effective(town, first.getUUID()), shared, "new staff inherit the worksite links");
            helper.assertValueEqual(save(fixture, first.getUUID(), personal), "", "one worker can use a chest override");
            helper.assertValueEqual(SettlementStorageService.effective(town, second.getUUID()), shared, "the second worker keeps its inherited barrel");
            SettlementWorkforceService.reconcile(helper.getLevel(), first);
            SettlementDepotService.synchronizeStorage(helper.getLevel(), first, town);
            var assignments = AssignedStorageSavedData.get(helper.getLevel()).assignedTo(first.getUUID());
            helper.assertTrue(assignments.size() == 1 && assignments.getFirst().pos().equals(fixture.container().north())
                    && assignments.getFirst().priority() == 1, "reconciliation retains the explicit container and priority");
            var settings = town.effectiveWorkerSettings(first.getUUID()).with(SettlementWorkerSetting.ENABLED, 0);
            helper.assertTrue(SettlementWorkerConfigurationService.configure(fixture.leader(), town, first.getUUID(), fixture.area().id(), settings), "unrelated options can change");
            helper.assertValueEqual(save(fixture, first.getUUID(), null), "", "reset clears only storage inheritance");
            helper.assertFalse(town.effectiveWorkerSettings(first.getUUID()).enabled(), "reset storage preserves the enabled override");
            helper.assertValueEqual(SettlementStorageService.effective(town, first.getUUID()), shared, "reset restores the shared links");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(SettlementStorageService.effective(restored, first.getUUID()), shared, "links and inheritance survive town reload");
        } finally { first.discard(); second.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void storageEditsRejectStaleStateAndRevokedPermissions(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        try {
            var original = new SettlementManagementActionPayload.SaveStorage(fixture.area().id(), null, true,
                    SettlementStorageLinks.AUTOMATIC, links(fixture.container(), SettlementStorageLinks.Purpose.SUPPLY, 2));
            helper.assertValueEqual(SettlementStorageService.configure(fixture.leader(), town, original), "", "the first edit succeeds");
            helper.assertValueEqual(SettlementStorageService.configure(fixture.leader(), town, original), "state_changed", "stale edits cannot overwrite the latest assignment");
            var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StorageDelegate"));
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            var previous = town.worksitePlans().get(fixture.area().id()).settings().storage();
            var clear = new SettlementManagementActionPayload.SaveStorage(fixture.area().id(), null, true, previous, new SettlementStorageLinks(false, List.of()));
            helper.assertValueEqual(SettlementStorageService.configure(delegate, town, clear), "permission_logistics", "staff alone cannot edit storage");
            helper.assertValueEqual(save(fixture, fixture.worker().getUUID(), links(fixture.container(), SettlementStorageLinks.Purpose.OUTPUT, 0)), "", "the worker has an explicit override");
            helper.assertFalse(SettlementWorksiteService.reset(delegate, town, fixture.worker().getUUID(), fixture.area().id(), SettlementStorageLinks.OVERRIDE),
                    "staff alone cannot reset explicit storage overrides");
            helper.assertTrue(SettlementWorkerConfigurationService.configure(delegate, town, fixture.worker().getUUID(), fixture.area().id(),
                    town.effectiveWorkerSettings(fixture.worker().getUUID()).with(SettlementWorkerSetting.ENABLED, 0)),
                    "staff can still change unrelated job options while explicit storage remains intact");
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
            helper.assertValueEqual(SettlementStorageService.configure(delegate, town, clear), "permission_staff", "logistics alone cannot change worker settings");
            helper.assertValueEqual(SettlementStorageService.validate(fixture.leader(), town, HiredVillagerRole.FARMING, previous,
                    links(town.center().east(town.radius() + 30), SettlementStorageLinks.Purpose.GENERAL, 0)), "storage_outside", "links cannot enlarge civic territory");
            helper.assertValueEqual(SettlementStorageService.validate(fixture.leader(), town, HiredVillagerRole.FARMING, previous,
                    links(town.center().above(8), SettlementStorageLinks.Purpose.GENERAL, 0)), "storage_incompatible", "air cannot be assigned as a container");
            helper.assertValueEqual(town.worksitePlans().get(fixture.area().id()).settings().storage(), previous, "rejections leave the assignment unchanged");
        } finally { fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void missingContainersStayConfiguredAndCanBeRemoved(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var configured = links(fixture.container(), SettlementStorageLinks.Purpose.OUTPUT, 3);
            helper.assertValueEqual(save(fixture, null, configured), "", "ordinary storage is configured");
            helper.getLevel().setBlockAndUpdate(fixture.container(), Blocks.AIR.defaultBlockState());
            SettlementDepotService.synchronizeStorage(helper.getLevel(), fixture.worker(), fixture.town());
            helper.assertValueEqual(SettlementStorageService.effective(fixture.town(), fixture.worker().getUUID()), configured, "destroying storage does not silently reset the plan");
            var snapshot = SettlementStorageService.snapshot(fixture.leader(), fixture.town(), fixture.area().id().toString());
            helper.assertValueEqual(snapshot.containers().getFirst().status(), "missing", "the editor reports a missing container");
            helper.assertValueEqual(save(fixture, null, links(fixture.container(), SettlementStorageLinks.Purpose.OUTPUT, 1)), "", "retained unavailable links can be reprioritized");
            helper.assertValueEqual(save(fixture, null, new SettlementStorageLinks(false, List.of())), "", "missing storage can be explicitly removed");
            helper.assertTrue(AssignedStorageSavedData.get(helper.getLevel()).assignedTo(fixture.worker().getUUID()).isEmpty(), "clearing explicit links does not rediscover depots");
        } finally { fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void liveStorageUsesExistingTransferRulesAndConservesItems(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        try {
            helper.assertValueEqual(save(fixture, null, links(fixture.container(), SettlementStorageLinks.Purpose.GENERAL, 0)), "", "general storage is linked");
            var container = (Container) level.getBlockEntity(fixture.container());
            var remainder = AssignedStorageService.depositStackAtAssignedStorage(fixture.worker(), fixture.container(), new ItemStack(Items.WHEAT, 12));
            helper.assertTrue(remainder.isEmpty() && container.countItem(Items.WHEAT) == 12, "live output reaches the linked barrel exactly once");
            SettlementTestPlots.planSupplies(level, fixture.town());
            var received = new java.util.ArrayList<ItemStack>();
            int moved = AssignedStorageService.transferItemsAtAssignedStorage(fixture.worker(), fixture.container(), stack -> stack.is(Items.WHEAT), 5,
                    stack -> { received.add(stack.copy()); return ItemStack.EMPTY; });
            helper.assertTrue(moved == 5 && container.countItem(Items.WHEAT) == 7 && received.stream().mapToInt(ItemStack::getCount).sum() == 5,
                    "live supply withdrawal conserves the source and received amounts");
            fixture.worker().setPos(fixture.container().east(20).getBottomCenter());
            remainder = AssignedStorageService.depositStackAtAssignedStorage(fixture.worker(), fixture.container(), new ItemStack(Items.WHEAT, 3));
            helper.assertTrue(remainder.getCount() == 3 && container.countItem(Items.WHEAT) == 7, "map assignment does not grant remote inventory reach");
            for (int slot = 0; slot < container.getContainerSize(); slot++) container.setItem(slot, new ItemStack(Items.STONE, 64));
            helper.assertValueEqual(SettlementStorageService.snapshot(fixture.leader(), fixture.town(), fixture.area().id().toString()).containers().getFirst().status(),
                    "full", "the inspector reports a full output container");
        } finally { fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void ordinaryLinksNeverGrantBackgroundAccessToUnrelatedDepots(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var town = fixture.town();
            var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Unrelated depot",
                    town.center().east(5), town.center().east(6).above(2), 0, true);
            helper.assertTrue(town.putArea(town.leader(), area), "a depot area is registered");
            var depot = town.registerDepot(town.leader(), area.id(), area.min(), SettlementDepotPurpose.GENERAL);
            depot.setItem(0, new ItemStack(Items.WHEAT, 32));
            helper.assertValueEqual(save(fixture, null, links(fixture.container(), SettlementStorageLinks.Purpose.GENERAL, 0)), "", "the worker uses only an ordinary barrel");
            var worker = SettlementWorkerState.capture(helper.getLevel(), fixture.worker(), SettlementWorkforceService.duty(fixture.worker()).orElseThrow(), 0);
            helper.assertFalse(SettlementWorksiteLinks.permitsSupply(worker.settings(), depot.id()) || SettlementWorksiteLinks.permitsOutput(worker.settings(), depot.id()),
                    "an explicit ordinary link cannot become an automatic depot wildcard");
            var supplies = SettlementProductionInventory.supplies(helper.getLevel(), town, worker, worker.items(helper.getLevel().registryAccess()), List.of(depot));
            helper.assertTrue(supplies.stream().noneMatch(supply -> supply.depot() != null), "simulation never reads ordinary inventory or unrelated depot stock");
            helper.assertValueEqual(depot.getItem(0).getCount(), 32, "unrelated depot inventory remains intact");
            helper.assertValueEqual(save(fixture, null, SettlementStorageLinks.AUTOMATIC), "", "automatic depots can be restored deliberately");
            helper.assertTrue(SettlementWorksiteService.links(town, fixture.area().id(), fixture.worker().getUUID()).permitsSupply(depot.id()), "legacy automatic depot behavior remains available");
        } finally { fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void framedFilterEditsUseExistingPoliciesAndRejectStaleItems(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.WHEAT));
        var frame = new ItemFrame(level, fixture.container().south(), Direction.SOUTH);
        frame.setItem(filter);
        level.addFreshEntity(frame);
        try {
            helper.assertValueEqual(save(fixture, null, links(fixture.container(), SettlementStorageLinks.Purpose.GENERAL, 0)), "", "framed storage is assigned");
            var snapshot = SettlementStorageService.snapshot(fixture.leader(), fixture.town(), fixture.area().id().toString());
            helper.assertTrue(snapshot.containers().getFirst().filters().size() == 1, "the map observes the actual attached filter");
            var command = new SettlementManagementActionPayload.StorageFilter(fixture.area().id(), fixture.container(), frame.getUUID(), filter,
                    VillagerFilterPolicy.PolicyField.DIRECTION, VillagerFilterPolicy.TransferDirection.RECEIVE.networkId());
            helper.assertValueEqual(SettlementStorageService.configureFilter(fixture.leader(), fixture.town(), command), "", "map controls update the physical policy");
            helper.assertValueEqual(VillagerFilterPolicy.read(frame.getItem()).direction(), VillagerFilterPolicy.TransferDirection.RECEIVE, "the shared filter policy changes");
            helper.assertValueEqual(SettlementStorageService.configureFilter(fixture.leader(), fixture.town(), command), "state_changed", "stale filter snapshots cannot overwrite a later edit");
            var rejected = AssignedStorageService.depositStackAtAssignedStorage(fixture.worker(), fixture.container(), new ItemStack(Items.DIRT, 8));
            helper.assertValueEqual(rejected.getCount(), 8, "ordinary links retain framed matcher restrictions");
            helper.assertTrue(((Container) level.getBlockEntity(fixture.container())).isEmpty(), "a filter rejection leaves the physical inventory unchanged");
        } finally { frame.discard(); fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void storageSettingsMigrateAndRoundTripWithinPacketLimits(GameTestHelper helper) {
        var defaults = SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING).settings();
        var legacy = defaults.save();
        legacy.putInt("Version", 3);
        legacy.remove("Storage");
        legacy.putInt("Overrides", defaults.overrides() & ~SettlementStorageLinks.OVERRIDE);
        helper.assertValueEqual(SettlementWorkerSettings.load(legacy), defaults, "old complete settings retain automatic depot assignment");
        legacy.putInt("Overrides", 0);
        helper.assertValueEqual(SettlementWorkerSettings.load(legacy).overrides(), 0, "old inheriting workers keep independent inheritance");
        var links = links(new BlockPos(-20, 65, 15), SettlementStorageLinks.Purpose.SUPPLY, 9);
        helper.assertValueEqual(SettlementWorkerSettings.load(defaults.withStorage(links).save()).storage(), links, "explicit settings survive NBT reload");
        var command = new SettlementManagementActionPayload(UUID.randomUUID(), new SettlementManagementActionPayload.SaveStorage(UUID.randomUUID(),
                UUID.randomUUID(), false, SettlementStorageLinks.AUTOMATIC, links));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "storage commands preserve scope and expected state on the wire");
            buffer.clear();
            buffer.writeBoolean(false);
            buffer.writeVarInt(SettlementStorageLinks.MAX_LINKS + 1);
            boolean rejected = false;
            try { SettlementStorageLinks.read(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "oversized storage payloads fail before allocating entries");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void connectedChestsRejectDuplicatesAndProtectedContainers(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var left = fixture.container();
        var right = left.east();
        try {
            level.setBlock(left, Blocks.CHEST.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT), 2);
            level.setBlock(right, Blocks.CHEST.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
            var duplicate = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(left, SettlementStorageLinks.Purpose.GENERAL, 0),
                    new SettlementStorageLinks.Link(right, SettlementStorageLinks.Purpose.GENERAL, 1)));
            helper.assertValueEqual(SettlementStorageService.validate(fixture.leader(), fixture.town(), HiredVillagerRole.FARMING,
                    SettlementStorageLinks.AUTOMATIC, duplicate), "storage_duplicate", "connected halves cannot duplicate one physical assignment");
            var block = level.getBlockEntity(right);
            var tag = block.saveWithFullMetadata(level.registryAccess());
            tag.putString("Lock", "Civic storage key");
            block.loadWithComponents(tag, level.registryAccess());
            helper.assertValueEqual(save(fixture, null, links(left, SettlementStorageLinks.Purpose.GENERAL, 0)), "storage_inaccessible",
                    "a lock on the other chest half is respected");
            helper.assertTrue(SettlementStorageService.effective(fixture.town(), fixture.worker().getUUID()).automatic(), "failed protected assignment preserves defaults");
        } finally { fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void remoteStorageSnapshotsDoNotLoadContainerChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var center = new BlockPos(18_000_000 + origin.getX() * 4, 80, 18_000_000 + origin.getZ() * 4);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RemoteStorage"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Remote farm", center, center.offset(2, 3, 2), 0, true);
        town.putArea(leader.getUUID(), area);
        var configured = links(center, SettlementStorageLinks.Purpose.GENERAL, 0);
        town.setWorksitePlan(leader.getUUID(), area.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING).settings().withStorage(configured), 1));
        helper.assertFalse(level.hasChunkAt(center), "the fixture starts unobserved");
        var snapshot = SettlementStorageService.snapshot(leader, town, area.id().toString());
        helper.assertValueEqual(snapshot.containers().getFirst().status(), "unloaded", "saved ordinary storage reports its unloaded state");
        helper.assertFalse(level.hasChunkAt(center), "inspection never forces the remote container chunk");
        helper.assertValueEqual(SettlementStorageService.validate(leader, town, HiredVillagerRole.FARMING,
                SettlementStorageLinks.AUTOMATIC, configured), "storage_unloaded", "new unobserved ordinary links are rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_storage")
    public static void framedMatcherDraftsSaveOnlyValidatedFilterConfiguration(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var original = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(original, 0, new ItemStack(Items.WHEAT));
        original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Shared wheat"));
        var frame = new ItemFrame(level, fixture.container().south(), Direction.SOUTH);
        frame.setItem(original);
        level.addFreshEntity(frame);
        try {
            helper.assertValueEqual(save(fixture, null, links(fixture.container(), SettlementStorageLinks.Purpose.GENERAL, 0)), "", "the container is assigned");
            var draft = original.copy();
            VillagerItemFilterData.setEntry(draft, 1, new ItemStack(Items.CARROT));
            draft.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Untrusted rename"));
            var command = new SettlementManagementActionPayload.SaveStorageFilter(fixture.area().id(), fixture.container(), frame.getUUID(), original, draft);
            helper.assertValueEqual(SettlementStorageService.saveFilter(fixture.leader(), fixture.town(), command), "", "the selected matcher draft can be saved");
            helper.assertTrue(VillagerItemFilterData.entry(frame.getItem(), 1).is(Items.CARROT), "the canonical item matcher stores the new entry");
            helper.assertValueEqual(frame.getItem().getHoverName().getString(), "Shared wheat", "matcher edits cannot rewrite unrelated physical item components");
            helper.assertValueEqual(frame.getItem().getCount(), 1, "editing never creates additional filter items");
            helper.assertValueEqual(SettlementStorageService.saveFilter(fixture.leader(), fixture.town(), command), "state_changed", "matcher drafts reject stale physical filters");
            var current = frame.getItem().copy();
            helper.assertValueEqual(SettlementStorageService.saveFilter(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.SaveStorageFilter(
                    fixture.area().id(), fixture.container(), frame.getUUID(), current, new ItemStack(Items.DIAMOND))), "storage_filter", "a filter cannot become another item");
            var attribute = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
            frame.setItem(attribute);
            var attributeDraft = attribute.copy();
            var choice = com.jvn.villagerretaliation.item.VillagerAttributeFilterData.availableAttributes(new ItemStack(Items.WHEAT), level).getFirst();
            com.jvn.villagerretaliation.item.VillagerAttributeFilterData.toggleSelected(attributeDraft, choice);
            helper.assertValueEqual(SettlementStorageService.saveFilter(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.SaveStorageFilter(
                    fixture.area().id(), fixture.container(), frame.getUUID(), attribute, attributeDraft)), "", "attribute matcher drafts use the shared attribute model");
            helper.assertTrue(com.jvn.villagerretaliation.item.VillagerAttributeFilterData.read(frame.getItem()).attributes().contains(choice), "selected attributes persist on the frame");
            var recipe = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            frame.setItem(recipe);
            var recipeDraft = recipe.copy();
            helper.assertTrue(com.jvn.villagerretaliation.item.VillagerRecipeFilterData.setRecipe(recipeDraft, level, net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick")), "the recipe fixture is supported");
            helper.assertValueEqual(SettlementStorageService.saveFilter(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.SaveStorageFilter(
                    fixture.area().id(), fixture.container(), frame.getUUID(), recipe, recipeDraft)), "", "recipe matcher drafts use the existing recipe validation");
            helper.assertTrue(com.jvn.villagerretaliation.item.VillagerRecipeFilterData.resolve(level, frame.getItem()).valid(), "the saved recipe remains executable");
            var payload = SettlementManagementService.snapshot(fixture.leader(), new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                    fixture.town().id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.STORAGE, 0, fixture.area().id().toString(), 1, false));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, payload);
                var decoded = com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer);
                var scope = (com.jvn.villagerretaliation.network.SettlementManagementPayload.Storage) decoded.entries().getFirst();
                helper.assertTrue(scope.area().equals(fixture.area().id()) && scope.resident() == null && scope.override()
                        && ItemStack.matches(scope.containers().getFirst().filters().getFirst().item(), frame.getItem()), "storage snapshots preserve scope and actual filter components on the wire");
            } finally { buffer.release(); }
        } finally { frame.discard(); fixture.worker().discard(); }
        helper.succeed();
    }

    private static String save(Fixture fixture, UUID resident, SettlementStorageLinks requested) {
        var settings = resident == null ? fixture.town().worksitePlans().get(fixture.area().id()).settings()
                : fixture.town().effectiveWorkerSettings(resident);
        return SettlementStorageService.configure(fixture.leader(), fixture.town(), new SettlementManagementActionPayload.SaveStorage(
                fixture.area().id(), resident, resident == null || (settings.overrides() & SettlementStorageLinks.OVERRIDE) != 0, settings.storage(), requested));
    }

    private static SettlementStorageLinks links(BlockPos position, SettlementStorageLinks.Purpose purpose, int priority) {
        return new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(position, purpose, priority)));
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "StorageLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Shared farm", center.offset(-2, -1, -2), center.offset(2, 3, 2), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the farm is registered");
        helper.assertTrue(town.setWorksitePlan(leader.getUUID(), area.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING)), "the farm has shared defaults");
        var container = center.east();
        level.setBlockAndUpdate(container, Blocks.BARREL.defaultBlockState());
        var fixture = new Fixture(town, area, null, leader, container);
        return new Fixture(town, area, worker(helper, fixture), leader, container);
    }

    private static Villager worker(GameTestHelper helper, Fixture fixture) {
        var level = helper.getLevel();
        var worker = EntityType.VILLAGER.create(level);
        worker.setPos(fixture.town().center().getBottomCenter());
        worker.setNoAi(true);
        worker.setNoGravity(true);
        worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, worker, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(worker);
        fixture.town().assignDuty(worker.getUUID(), fixture.area().id());
        SettlementWorkforceService.reconcile(level, worker);
        HiredJobInventory.getJobInventory(worker).clearContent();
        return worker;
    }

    private record Fixture(SettlementRecord town, SettlementArea area, Villager worker, ServerPlayer leader, BlockPos container) { }
}
