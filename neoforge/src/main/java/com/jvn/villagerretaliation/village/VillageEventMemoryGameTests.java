package com.jvn.villagerretaliation.village;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageEventMemoryGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageEventMemoryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void memoryActionScopesParseAndRejectInvalidValues(GameTestHelper helper) {
        DatapackDiagnostics.clear();
        JsonObject root = JsonParser.parseString("""
                {
                  "actions": [
                    {"type":"memory","memory_event":"test:villager","memory_scope":"villager"},
                    {"type":"memory","memory_event":"test:village","memory_scope":"village"},
                    {"type":"memory","memory_event":"test:both","memory_scope":"both"},
                    {"type":"memory","memory_event":"test:default"},
                    {"type":"memory","memory_event":"test:invalid","memory_scope":"nearby"}
                  ]
                }
                """).getAsJsonObject();
        List<VillagerActionDefinition> actions = VillagerActionDefinition.readList(
                VillagerRetaliation.id("test/memory_scopes"), "memory scope test", root);
        helper.assertValueEqual(actions.size(), 4, "invalid memory scope action was not rejected");
        helper.assertValueEqual(actions.get(0).memoryScope(), VillageEventMemory.MemoryScope.VILLAGER,
                "villager memory scope");
        helper.assertValueEqual(actions.get(1).memoryScope(), VillageEventMemory.MemoryScope.VILLAGE,
                "village memory scope");
        helper.assertValueEqual(actions.get(2).memoryScope(), VillageEventMemory.MemoryScope.BOTH,
                "both memory scope");
        helper.assertValueEqual(actions.get(3).memoryScope(), VillageEventMemory.MemoryScope.BOTH,
                "omitted memory scope default");
        SceneContinuation continuation = new SceneContinuation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                "test",
                actions,
                0,
                Map.of());
        List<VillagerActionDefinition> restoredActions = SceneContinuation.load(continuation.save()).actions();
        helper.assertValueEqual(restoredActions.stream().map(VillagerActionDefinition::memoryScope).toList(),
                actions.stream().map(VillagerActionDefinition::memoryScope).toList(),
                "scene continuation memory scopes");
        helper.assertTrue(DatapackDiagnostics.recent().stream()
                        .anyMatch(entry -> entry.message().contains("invalid memory_scope")),
                "invalid memory scope did not emit a datapack diagnostic");
        DatapackDiagnostics.clear();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void scopedWritesUsePhysicalVillageAndKeepPersonalMemory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos eventPos = helper.absolutePos(new BlockPos(2, 2, 2));
        OccupiedHome home = addOccupiedHome(level, eventPos);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId physicalVillage = registry.discoverAt(level, eventPos).orElseThrow();
        VillageAllegianceId distantHome = registry.create(
                level.getGameTime(), level.dimension().location(), eventPos.offset(512, 0, 0), "Distant Home");
        Villager visitor = spawnVillager(level, eventPos);
        Villager local = spawnVillager(level, eventPos.offset(1, 0, 0));
        Villager distantResident = spawnVillager(level, eventPos.offset(2, 0, 0));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VillageAllegianceApi.assignKnown(level, visitor, distantHome, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, local, physicalVillage, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, distantResident, distantHome, AllegianceAssignmentSource.ADMIN);

        try {
            ResourceLocation tag = VillagerRetaliation.id("test_physical_village_memory");
            VillageEventMemory.WriteResult result = VillageEventMemory.remember(
                    level, tag, eventPos, visitor, player, VillageEventMemory.MemoryScope.BOTH);
            helper.assertTrue(result.villagerChanged(), "visitor personal bucket was not written");
            helper.assertTrue(result.villageChanged(), "physical village bucket was not written");
            helper.assertTrue(result.dispatched(), "first logical incident did not dispatch");
            helper.assertTrue(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillager(level, visitor), Set.of(tag)),
                    "visitor did not carry personal memory");
            helper.assertTrue(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillage(level, local), Set.of(tag)),
                    "local resident could not read its communal memory");
            helper.assertFalse(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillage(level, distantResident), Set.of(tag)),
                    "the source villager's distant home received leaked communal memory");

            ResourceLocation personalOnly = VillagerRetaliation.id("test_personal_scope");
            VillageEventMemory.remember(
                    level, personalOnly, eventPos, visitor, player, VillageEventMemory.MemoryScope.VILLAGER);
            helper.assertTrue(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillager(level, visitor), Set.of(personalOnly)),
                    "villager scope missed personal memory");
            helper.assertFalse(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillage(level, physicalVillage), Set.of(personalOnly)),
                    "villager scope leaked into communal memory");

            ResourceLocation villageOnly = VillagerRetaliation.id("test_village_scope");
            VillageEventMemory.remember(
                    level, villageOnly, eventPos, visitor, player, VillageEventMemory.MemoryScope.VILLAGE);
            helper.assertFalse(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillager(level, visitor), Set.of(villageOnly)),
                    "village scope leaked into personal memory");
            helper.assertTrue(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillage(level, physicalVillage), Set.of(villageOnly)),
                    "village scope missed communal memory");

            helper.assertFalse(VillageEventMemory.remember(
                    level,
                    VillageEventMemory.EventTag.BABY_BORN,
                    eventPos.offset(1024, 0, 0),
                    visitor,
                    player),
                    "village-only event without an exact village should not be retained");
        } finally {
            visitor.discard();
            local.discard();
            distantResident.discard();
            registry.archive(physicalVillage);
            registry.archive(distantHome);
            home.remove(level);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void reassignmentAndAliasesSelectCanonicalCommunalMemory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos eventPos = helper.absolutePos(new BlockPos(2, 2, 2));
        OccupiedHome occupiedHome = addOccupiedHome(level, eventPos);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId oldHome = registry.discoverAt(level, eventPos).orElseThrow();
        VillageAllegianceId newHome = registry.create(
                level.getGameTime(), level.dimension().location(), eventPos.offset(512, 0, 0), "New Home");
        Villager villager = spawnVillager(level, eventPos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VillageAllegianceApi.assignKnown(level, villager, oldHome, AllegianceAssignmentSource.ADMIN);
        ResourceLocation tag = VillagerRetaliation.id("test_reassignment_memory");

        try {
            VillageEventMemory.remember(level, tag, eventPos, villager, player, VillageEventMemory.MemoryScope.BOTH);
            VillageAllegianceApi.assignKnown(level, villager, newHome, AllegianceAssignmentSource.ADMIN);
            helper.assertTrue(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillager(level, villager), Set.of(tag)),
                    "reassignment erased personal memory");
            helper.assertFalse(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillage(level, villager), Set.of(tag)),
                    "reassigned villager retained former communal access");

            helper.assertTrue(registry.merge(oldHome, newHome), "canonical merge failed");
            helper.assertTrue(VillageEventMemory.hasAnyTag(
                    VillageEventMemory.recentForVillage(level, villager), Set.of(tag)),
                    "canonical merge did not expose aliased communal memory");
        } finally {
            villager.discard();
            registry.archive(oldHome);
            registry.archive(newHome);
            occupiedHome.remove(level);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dualWitnessWriteDispatchesLogicalIncidentOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos eventPos = helper.absolutePos(new BlockPos(2, 2, 2));
        OccupiedHome occupiedHome = addOccupiedHome(level, eventPos);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId village = registry.discoverAt(level, eventPos).orElseThrow();
        Villager first = spawnVillager(level, eventPos);
        Villager second = spawnVillager(level, eventPos.offset(1, 0, 0));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VillageAllegianceApi.assignKnown(level, first, village, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, second, village, AllegianceAssignmentSource.ADMIN);

        try {
            VillageEventMemory.WriteResult firstWrite = VillageEventMemory.remember(
                    level,
                    VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT,
                    eventPos,
                    first,
                    player,
                    VillageEventMemory.MemoryScope.BOTH);
            VillageEventMemory.WriteResult secondWrite = VillageEventMemory.remember(
                    level,
                    VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT,
                    eventPos,
                    second,
                    player,
                    VillageEventMemory.MemoryScope.BOTH);
            helper.assertTrue(firstWrite.villagerChanged() && firstWrite.villageChanged() && firstWrite.dispatched(),
                    "first witness did not write and dispatch both scopes");
            helper.assertTrue(secondWrite.villagerChanged(), "second witness did not receive a personal copy");
            helper.assertFalse(secondWrite.villageChanged(), "second witness duplicated the communal incident");
            helper.assertFalse(secondWrite.dispatched(), "second witness dispatched the same logical incident twice");
        } finally {
            first.discard();
            second.discard();
            registry.archive(village);
            occupiedHome.remove(level);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void memoryBucketsRoundTripCapAndExpireIndependently(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID villagerId = UUID.randomUUID();
        VillageAllegianceId villageId = VillageAllegianceId.random();
        ResourceLocation tag = VillagerRetaliation.id("test_persistence_memory");
        VillageEventMemory.MemoryEvent event = new VillageEventMemory.MemoryEvent(
                null,
                tag,
                level.getGameTime(),
                level.dimension(),
                BlockPos.ZERO,
                villagerId,
                null,
                null,
                null,
                null,
                null,
                null);
        VillageEventMemorySavedData data = new VillageEventMemorySavedData();
        data.villagerEventsForWrite(villagerId).add(event);
        data.villageEventsForWrite(villageId).add(event);
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        VillageEventMemorySavedData restored = VillageEventMemorySavedData.load(saved, level.registryAccess());
        helper.assertValueEqual(restored.villagerEvents(villagerId).size(), 1, "personal bucket round-trip");
        helper.assertValueEqual(restored.villageEvents(villageId).size(), 1, "village bucket round-trip");
        helper.assertValueEqual(restored.villagerEvents(villagerId).getFirst().dimension(), level.dimension(),
                "event dimension round-trip");

        Villager villager = spawnVillager(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        for (int index = 0; index < 85; index++) {
            VillageEventMemory.remember(
                    level,
                    VillagerRetaliation.id("test_cap_" + index),
                    villager.blockPosition(),
                    villager,
                    null,
                    VillageEventMemory.MemoryScope.VILLAGER);
        }
        helper.assertValueEqual(VillageEventMemory.recentForVillager(level, villager).size(), 80,
                "personal bucket cap");

        UUID expiredOwner = UUID.randomUUID();
        VillageEventMemorySavedData global = VillageEventMemorySavedData.get(level);
        global.villagerEventsForWrite(expiredOwner).add(new VillageEventMemory.MemoryEvent(
                null,
                VillagerRetaliation.id("test_expired"),
                level.getGameTime() - 20L * 60L * 10L - 1L,
                level.dimension(),
                BlockPos.ZERO,
                expiredOwner,
                null,
                null,
                null,
                null,
                null,
                null));
        VillageEventMemory.clear();
        helper.assertTrue(VillageEventMemory.recentForVillager(level, expiredOwner).isEmpty(),
                "expired personal bucket event survived TTL pruning");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void legacyMemoryMigrationKeepsPersonalAndDropsUnresolvedCommunal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos villagePos = helper.absolutePos(new BlockPos(2, 2, 2));
        OccupiedHome occupiedHome = addOccupiedHome(level, villagePos);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId village = registry.discoverAt(level, villagePos).orElseThrow();
        UUID resolvedSource = UUID.randomUUID();
        UUID unresolvedSource = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag();
        ListTag entries = new ListTag();
        entries.add(legacyEvent(level, villagePos, resolvedSource, "test_legacy_resolved"));
        entries.add(legacyEvent(
                level,
                new BlockPos(villagePos.getX(), level.getMaxBuildHeight() - 2, villagePos.getZ()),
                unresolvedSource,
                "test_legacy_unresolved"));
        legacy.put("Entries", entries);

        try {
            VillageEventMemorySavedData migrated = VillageEventMemorySavedData.load(legacy, level.registryAccess());
            helper.assertTrue(migrated.migrateLegacy(level), "legacy migration did not run");
            helper.assertValueEqual(migrated.villagerEvents(resolvedSource).size(), 1,
                    "resolved legacy personal memory");
            helper.assertValueEqual(migrated.villagerEvents(unresolvedSource).size(), 1,
                    "unresolved legacy personal memory");
            helper.assertValueEqual(migrated.villageEvents(village).size(), 1,
                    "resolved legacy communal memory");
            helper.assertValueEqual(migrated.villageBucketsById().size(), 1,
                    "unresolved legacy event leaked into a communal bucket");
        } finally {
            registry.archive(village);
            occupiedHome.remove(level);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void canonicalEncounterUsesDurableResidentRoster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId source = registry.create(
                level.getGameTime(), level.dimension().location(), BlockPos.ZERO, "Encounter Source");
        VillageAllegianceId target = registry.create(
                level.getGameTime(), level.dimension().location(), new BlockPos(512, 0, 0), "Encounter Target");
        Villager first = spawnVillager(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        Villager second = spawnVillager(level, helper.absolutePos(new BlockPos(3, 2, 2)));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VillageAllegianceApi.assignKnown(level, first, source, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, second, target, AllegianceAssignmentSource.ADMIN);
        helper.assertTrue(registry.merge(source, target), "encounter alias merge failed");

        helper.assertTrue(VillagerInteractionTracker.getState(level, first, player).firstVillageInteraction(),
                "first canonical village encounter was not detected");
        VillagerInteractionTracker.rememberConversationOpened(level, first, player);
        helper.assertFalse(VillagerInteractionTracker.getState(level, second, player).firstVillageInteraction(),
                "canonical alias was treated as a separate village encounter");
        first.discard();
        second.discard();
        registry.archive(source);
        registry.archive(target);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void offlineQuestMemoryUsesPersonalAndExactCommunalBuckets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos firstPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos secondPos = firstPos.offset(80, 0, 0);
        OccupiedHome firstHome = addOccupiedHome(level, firstPos);
        OccupiedHome secondHome = addOccupiedHome(level, secondPos);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId firstVillage = registry.discoverAt(level, firstPos).orElseThrow();
        VillageAllegianceId secondVillage = registry.discoverAt(level, secondPos).orElseThrow();
        Villager source = spawnVillager(level, firstPos);
        Villager neighbor = spawnVillager(level, firstPos.offset(1, 0, 0));
        Villager outsider = spawnVillager(level, secondPos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VillageAllegianceApi.assignKnown(level, source, firstVillage, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, neighbor, firstVillage, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, outsider, secondVillage, AllegianceAssignmentSource.ADMIN);
        ResourceLocation tag = VillagerRetaliation.id("test_offline_memory");

        try {
            VillageEventMemory.remember(level, tag, firstPos, source, player, VillageEventMemory.MemoryScope.BOTH);
            DialogueCondition.Memory thisVillager = new DialogueCondition.Memory(
                    Set.of(tag), DialogueCondition.MemorySource.THIS_VILLAGER, true, DialogueCondition.MemoryKind.EVENT_TAG);
            DialogueCondition.Memory otherVillager = new DialogueCondition.Memory(
                    Set.of(tag), DialogueCondition.MemorySource.OTHER_VILLAGER, true, DialogueCondition.MemoryKind.EVENT_TAG);

            helper.assertTrue(VillagerQuestService.debugEventTagMemoryMatchesWithoutLiveContextForTests(
                    player, level, progressFor(source, firstPos, level), thisVillager),
                    "offline personal memory did not match this_villager");
            helper.assertFalse(VillagerQuestService.debugEventTagMemoryMatchesWithoutLiveContextForTests(
                    player, level, progressFor(source, firstPos, level), otherVillager),
                    "offline communal query treated the speaker's event as other_villager");
            helper.assertTrue(VillagerQuestService.debugEventTagMemoryMatchesWithoutLiveContextForTests(
                    player,
                    level,
                    progressFor(neighbor, firstPos.atY(level.getMaxBuildHeight() - 2), level),
                    otherVillager),
                    "offline same-village neighbor could not read communal memory");
            helper.assertFalse(VillagerQuestService.debugEventTagMemoryMatchesWithoutLiveContextForTests(
                    player,
                    level,
                    progressFor(outsider, secondPos.atY(level.getMaxBuildHeight() - 2), level),
                    otherVillager),
                    "offline unrelated village read another village's communal memory");
        } finally {
            source.discard();
            neighbor.discard();
            outsider.discard();
            registry.archive(firstVillage);
            registry.archive(secondVillage);
            firstHome.remove(level);
            secondHome.remove(level);
        }
        helper.succeed();
    }

    private static VillagerQuestSavedData.QuestProgress progressFor(
            Villager villager,
            BlockPos villagePos,
            ServerLevel level) {
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(villager.getUUID(), level.dimension(), villagePos, level.getGameTime());
        progress.setIssuer(
                villager.getUUID(),
                "Test Villager",
                "minecraft:none",
                1,
                level.dimension(),
                villagePos,
                VillageScopeKeys.forPosition(level.dimension(), villagePos));
        return progress;
    }

    private static Villager spawnVillager(ServerLevel level, BlockPos pos) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new IllegalStateException("Could not create villager");
        }
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(villager);
        return villager;
    }

    private static CompoundTag legacyEvent(
            ServerLevel level,
            BlockPos pos,
            UUID source,
            String path) {
        CompoundTag event = new CompoundTag();
        event.putString("Dimension", level.dimension().location().toString());
        event.putString("TagId", VillagerRetaliation.id(path).toString());
        event.putLong("GameTime", level.getGameTime());
        event.putInt("X", pos.getX());
        event.putInt("Y", pos.getY());
        event.putInt("Z", pos.getZ());
        event.putUUID("SourceId", source);
        return event;
    }

    private static OccupiedHome addOccupiedHome(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        Holder<PoiType> home = level.registryAccess()
                .registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME);
        PoiManager manager = level.getPoiManager();
        manager.add(pos, home);
        manager.take(type -> type.is(PoiTypes.HOME), (type, candidate) -> candidate.equals(pos), pos, 1)
                .orElseThrow(() -> new IllegalStateException("Could not occupy test home POI"));
        return new OccupiedHome(pos);
    }

    private record OccupiedHome(BlockPos pos) {
        private void remove(ServerLevel level) {
            PoiManager manager = level.getPoiManager();
            if (manager.getFreeTickets(this.pos) == 0) {
                manager.release(this.pos);
            }
            manager.remove(this.pos);
        }
    }
}
