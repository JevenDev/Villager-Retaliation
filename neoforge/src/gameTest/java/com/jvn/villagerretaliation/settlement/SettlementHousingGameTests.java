package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHousingGameTests {
    private SettlementHousingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void managementAssignmentsUpdateNativeHomesAndReleaseOnlyTheSelectedBed(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, true);
        var first = beds(fixture).getFirst();
        var second = beds(fixture).getLast();
        try {
            helper.assertTrue(command(fixture, resident, first, true), "the leader assigns a usable bed through management");
            helper.assertValueEqual(resident.getBrain().getMemory(MemoryModuleType.HOME).orElseThrow().pos(), first, "the native resident goes home to the assigned bed");
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(first), 0, "the native bed claim is occupied once");
            helper.assertTrue(command(fixture, resident, second, true), "the resident can move to another bed");
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(first), 1, "moving releases the former native claim");
            helper.assertFalse(command(fixture, resident, first, false), "a stale release cannot clear the newer assignment");
            helper.assertTrue(command(fixture, resident, second, false), "the current bed can be released");
            helper.assertTrue(resident.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "release clears the matching native home");
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(second), 1, "release returns the native ticket once");
            SettlementHousingService.reconcile(resident);
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(second), 1, "reconciliation does not release another ticket");
        } finally {
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void unloadedReservationsBlockDuplicateAssignmentsAndNativeAcquisition(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var first = resident(helper, fixture, false);
        var second = resident(helper, fixture, false);
        var bed = beds(fixture).getFirst();
        helper.assertTrue(command(fixture, first, bed, true), "an unloaded resident receives the bed reservation");
        helper.assertFalse(command(fixture, second, bed, true), "two residents cannot own the same bed");
        var type = helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME);
        helper.assertTrue(AcquirePoi.findPathToPois(second, Set.of(Pair.of(type, bed))) == null, "native home searches respect an unloaded resident's reservation");
        helper.assertTrue(SettlementHousingService.canAcquire(helper.getLevel(), bed, first.getUUID()), "the owner retains access to its reserved bed");
        helper.assertFalse(SettlementHousingService.canAcquire(helper.getLevel(), beds(fixture).getLast(), first.getUUID()), "the owner cannot silently acquire a second home");
        helper.assertTrue(helper.getLevel().getEntity(first.getUUID()) == null, "housing does not spawn an unloaded resident");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void birthBedSearchCannotConsumeAReservedUnloadedResidentsHome(GameTestHelper helper) throws ReflectiveOperationException {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var owner = resident(helper, fixture, false);
        var parent = resident(helper, fixture, false);
        var bed = beds(fixture).getFirst();
        helper.assertTrue(command(fixture, owner, bed, true), "the empty native bed belongs to an unloaded resident");
        var type = helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME);
        var method = VillagerMakeLove.class.getDeclaredMethod("canReach", Villager.class, BlockPos.class, Holder.class);
        method.setAccessible(true);
        helper.assertFalse((boolean) method.invoke(new VillagerMakeLove(), parent, bed, type), "the real birth behavior excludes reserved beds before finding a path");
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 1, "the unloaded reservation has not manufactured a native claimant");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void nativeHomesAreAdoptedWithoutTakingASecondTicket(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, false);
        var bed = beds(fixture).getFirst();
        helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 0).orElseThrow();
        resident.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), bed));
        SettlementCareService.onUnloaded(resident);
        helper.assertValueEqual(fixture.town().homes().get(resident.getUUID()).bed(), bed, "a previously acquired native home becomes the resident's town home");
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 0, "adoption preserves the original native claim");
        SettlementHousingService.reconcile(resident);
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 0, "repeated adoption does not consume another ticket");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void staleNativeHomesCannotReleaseTheNewOwnersClaimAfterRemoteReassignment(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, true);
        var first = beds(fixture).getFirst();
        var second = beds(fixture).getLast();
        helper.assertTrue(command(fixture, resident, first, true), "the first native home is assigned");
        SettlementCareService.onUnloaded(resident);
        var stale = resident.saveWithoutId(new CompoundTag());
        resident.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(command(fixture, resident, second, true), "the absent resident can change beds");
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(first), 1, "the observed old native claim is released while the resident is absent");
        var nextOwner = resident(helper, fixture, true);
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        try {
            helper.assertTrue(command(fixture, nextOwner, first, true), "another resident can take the released bed");
            var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(saved.homes().get(resident.getUUID()).bed(), second, "the current home survives saving");
            returning.load(stale);
            helper.assertTrue(helper.getLevel().addFreshEntity(returning), "the original entity returns with its stale home memory");
            SettlementHousingService.reconcile(returning);
            helper.assertValueEqual(returning.getBrain().getMemory(MemoryModuleType.HOME).orElseThrow().pos(), second, "the returning resident adopts its current assignment");
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(first), 0, "the old home receipt protects its new owner");
            SettlementHousingService.reconcile(returning);
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(first), 0, "replaying the handoff cannot free another resident's bed");
        } finally {
            returning.discard();
            nextOwner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void housingRequiresStaffPermissionAndCurrentMembership(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, false);
        var bed = beds(fixture).getFirst();
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HousingDelegate"));
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
        helper.assertFalse(SettlementHousingService.assign(delegate, fixture.town(), resident.getUUID(), bed), "construction permission cannot assign residents to beds");
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertTrue(SettlementHousingService.assign(delegate, fixture.town(), resident.getUUID(), bed), "staff delegates can assign housing");
        VillageAllegianceRegistrySavedData.get(helper.getLevel()).removeResident(fixture.town().id(), resident.getUUID());
        helper.assertFalse(SettlementHousingService.assign(delegate, fixture.town(), resident.getUUID(), beds(fixture).getLast()),
                "a stale resident row cannot assign housing after census removal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void housingIncludesChildrenAndPreservesPersonalContracts(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var child = resident(helper, fixture, false);
        child.setAge(-24_000);
        SettlementCareService.onUnloaded(child);
        helper.assertTrue(command(fixture, child, beds(fixture).getFirst(), true), "children can have individual beds");
        var hired = resident(helper, fixture, true);
        try {
            var before = VillagerAssignmentStore.hire(hired, UUID.randomUUID(), HiredVillagerRole.CRAFTSMAN, 10L, fixture.town().center());
            helper.assertTrue(command(fixture, hired, beds(fixture).getLast(), true), "an enrolled resident keeps housing during personal employment");
            helper.assertValueEqual(VillagerAssignmentStore.snapshot(hired), before, "housing changes neither the personal contract nor its anchor");
            helper.assertTrue(fixture.town().duties().isEmpty(), "bed assignments do not create civic work commitments");
        } finally {
            hired.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void missingBedsAndDisabledHomesInvalidateAssignments(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, true);
        var bed = beds(fixture).getFirst();
        try {
            helper.assertTrue(command(fixture, resident, bed, true), "the initial bed can be assigned");
            helper.getLevel().setBlock(bed, Blocks.AIR.defaultBlockState(), 3);
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), fixture.home());
            SettlementHousingService.reconcile(resident);
            helper.assertTrue(fixture.town().homes().get(resident.getUUID()).bed() == null, "a destroyed bed releases its assignment");
            helper.assertTrue(resident.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "the native resident stops targeting the destroyed bed");
            var other = beds(fixture).getFirst();
            helper.assertTrue(command(fixture, resident, other, true), "the remaining bed can replace it");
            var disabled = new SettlementArea(fixture.home().id(), fixture.home().type(), fixture.home().name(),
                    fixture.home().min(), fixture.home().max(), fixture.home().priority(), false);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), disabled), "the home designation can be disabled");
            SettlementHousingService.reconcile(resident);
            helper.assertTrue(fixture.town().homes().get(resident.getUUID()).bed() == null, "disabled housing does not retain a private bed claim");
        } finally {
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void occupiedNativeBedsCannotBeTakenFromUnmanagedClaimants(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, false);
        var bed = beds(fixture).getFirst();
        helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 0).orElseThrow();
        helper.assertFalse(command(fixture, resident, bed, true), "an observed native claim cannot be overwritten by a town assignment");
        helper.assertTrue(fixture.town().homes().isEmpty(), "denial creates no competing owner");
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 0, "the original native claim remains occupied");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void recordedUnloadedBedsRemainAssignableWithoutInventingLegacyAvailability(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(1, 2, 1)).offset(7_500_000, 0, 7_500_000);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RemoteHousing"));
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center, center.offset(4, 3, 4), 5, true);
        town.putArea(town.leader(), area);
        var bed = center.offset(1, 0, 1);
        town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0L, List.of(bed), List.of(), true));
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(center.getBottomCenter());
        VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
        SettlementCareService.onUnloaded(resident);
        helper.assertFalse(SettlementHousingService.assign(leader, town, resident.getUUID(), bed), "legacy bed counts do not invent vacant native claims");
        town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(1L, List.of(bed), List.of(), true, Map.of(), List.of(), Map.of(bed, true)));
        helper.assertTrue(SettlementHousingService.assign(leader, town, resident.getUUID(), bed), "recorded vacant beds remain usable while unloaded");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertTrue(Boolean.TRUE.equals(restored.surveys().get(area.id()).bedAvailability().get(bed)), "the availability observation survives saving");
        helper.assertValueEqual(restored.homes().get(resident.getUUID()).bed(), bed, "the unloaded reservation survives saving");
        helper.assertFalse(level.hasChunkAt(center), "remote housing never forces the town chunk to load");
        helper.assertTrue(level.getEntity(resident.getUUID()) == null, "remote housing never spawns the resident");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void bedPagesAndCommandsRoundTripWithOccupancyAndResidentSearch(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, false);
        var bed = beds(fixture).getFirst();
        var command = new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.Home(resident.getUUID(), bed, true));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "home commands preserve the resident and exact bed");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command), "the decoded command uses staffing authority");
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    fixture.town().id().value(), SettlementManagementRequestPayload.Page.BEDS, 0, resident.getUUID().toString(), 1, false));
            helper.assertValueEqual(snapshot.entries().size(), 1, "beds can be found by their resident's identity");
            var row = (SettlementManagementPayload.Bed) snapshot.entries().getFirst();
            helper.assertValueEqual(row.resident(), resident.getUUID(), "the bed page identifies its owner");
            helper.assertFalse(row.available(), "the reservation is not offered to another resident");
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
            helper.assertValueEqual(SettlementManagementPayload.STREAM_CODEC.decode(buffer), snapshot, "bed pages round trip with occupancy");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void savedHomeAssignmentsRejectDuplicateBedsAndRetainLegacyCompatibility(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, false);
        helper.assertTrue(command(fixture, resident, beds(fixture).getFirst(), true), "the current resident owns a bed");
        var tag = fixture.town().save(helper.getLevel().registryAccess());
        var duplicate = tag.getList("Homes", Tag.TAG_COMPOUND).getCompound(0).copy();
        duplicate.putUUID("Resident", UUID.randomUUID());
        tag.getList("Homes", Tag.TAG_COMPOUND).add(duplicate);
        boolean rejected = false;
        try {
            SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a saved bed cannot have two owners");
        tag.remove("Homes");
        tag.remove("HomeReleases");
        var legacy = SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(legacy.homes().isEmpty(), "older towns do not invent assigned homes");
        helper.assertValueEqual(legacy.housingCapacity(), fixture.town().housingCapacity(), "legacy usable bed capacity remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void aNativeHomeAcquiredJustBeforeUnloadingStaysReserved(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, true);
        var bed = beds(fixture).getFirst();
        helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 0).orElseThrow();
        resident.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), bed));
        resident.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertValueEqual(fixture.town().homes().get(resident.getUUID()).bed(), bed, "the unload event records the last native home acquisition");
        helper.assertFalse(SettlementHousingService.canUse(helper.getLevel(), bed, UUID.randomUUID()), "the absent owner's newly acquired bed stays protected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void deferredNativeClaimReleasesSurviveSavingAndCannotReplay(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        var bed = fixture.town().center().offset(7_600_000, 0, 7_600_000).atY(80);
        var releaseId = UUID.randomUUID();
        fixture.town().releaseHome(releaseId, UUID.randomUUID(), GlobalPos.of(level.dimension(), bed));
        SettlementHousingService.update(level, fixture.town());
        helper.assertFalse(level.hasChunkAt(bed), "releasing a distant home does not force its chunk to load");
        helper.assertFalse(SettlementHousingService.canUse(level, bed, null), "pending native cleanup keeps the old claim reserved");
        var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        var release = restored.homeReleases().get(releaseId);
        helper.assertTrue(release != null, "the pending release survives saving");
        level.getChunkAt(bed);
        level.getPoiManager().add(bed, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 0).orElseThrow();
        SettlementHousingService.update(level, fixture.town());
        helper.assertValueEqual(level.getPoiManager().getFreeTickets(bed), 1, "the observed former claim is released once");
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 0).orElseThrow();
        fixture.town().releaseHome(release.id(), release.resident(), release.home());
        SettlementHousingService.update(level, fixture.town());
        helper.assertValueEqual(level.getPoiManager().getFreeTickets(bed), 0, "a stale saved release cannot free the newer native claim");
        helper.assertTrue(fixture.town().homeReleases().isEmpty(), "the acknowledged release is retired again");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void partiallyUnloadedBedsRetainCurrentNativeAvailability(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1)).offset(7_700_000, 0, 7_700_000);
        var head = new BlockPos(origin.getX(), 80, (origin.getZ() & ~15) + 15);
        var foot = head.south();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), head, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), head, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Border home", head.offset(-1, -1, -1), foot.offset(1, 2, 1), 5, true);
        town.putArea(town.leader(), area);
        var chunk = level.getChunkAt(head);
        chunk.setBlockState(head, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), false);
        level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
        town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0L, List.of(head), List.of(), true,
                Map.of(), List.of(), Map.of(head, false)));
        helper.assertFalse(level.hasChunkAt(foot), "the other half of the bed is unloaded");
        SettlementBuildingService.refresh(level, town, area);
        helper.assertTrue(Boolean.TRUE.equals(SettlementHousingService.availability(town, head)),
                "a retained bed with a loaded head uses its current free native claim");
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(head), head, 0).orElseThrow();
        SettlementBuildingService.refresh(level, town, area);
        helper.assertTrue(Boolean.FALSE.equals(SettlementHousingService.availability(town, head)),
                "partial observation also records a newly occupied claim");
        helper.assertFalse(level.hasChunkAt(foot), "checking the claim does not load the missing bed half");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing")
    public static void disablingHousingReleasesAnAbsentResidentsObservedNativeClaim(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var resident = resident(helper, fixture, true);
        var bed = beds(fixture).getFirst();
        helper.assertTrue(command(fixture, resident, bed, true), "the resident owns the initial native home");
        resident.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var disabled = new SettlementArea(fixture.home().id(), fixture.home().type(), fixture.home().name(),
                fixture.home().min(), fixture.home().max(), fixture.home().priority(), false);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), disabled), "the home designation is disabled");
        SettlementHousingService.update(helper.getLevel(), fixture.town());
        helper.assertTrue(fixture.town().homes().get(resident.getUUID()).bed() == null, "the absent resident loses the disabled assignment");
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 1,
                "the observed old native claim is released without waiting for its resident to return");
        helper.assertTrue(helper.getLevel().getEntity(resident.getUUID()) == null, "cleanup does not spawn the absent resident");
        helper.succeed();
    }

    private static Villager resident(GameTestHelper helper, SettlementPopulationGameTests.Fixture fixture, boolean loaded) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.town().center().getBottomCenter());
        villager.setNoAi(true);
        VillageAllegianceApi.assignKnown(helper.getLevel(), villager, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        if (loaded) helper.assertTrue(helper.getLevel().addFreshEntity(villager), "the native resident is tracked");
        SettlementCareService.onUnloaded(villager);
        return villager;
    }

    private static List<BlockPos> beds(SettlementPopulationGameTests.Fixture fixture) {
        return fixture.town().surveys().get(fixture.home().id()).beds();
    }

    private static boolean command(SettlementPopulationGameTests.Fixture fixture, Villager resident, BlockPos bed, boolean assign) {
        return SettlementManagementService.execute(fixture.leader(), new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.Home(resident.getUUID(), bed, assign)));
    }
}
