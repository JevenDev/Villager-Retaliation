package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData;
import com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring_playtest")
@PrefixGameTestTemplate(false)
public final class QuestPlaytestObjectiveGameTests {
    private static final Map<QuestDefinition.ObjectiveType, String> OBJECTIVES = Map.ofEntries(
            Map.entry(QuestDefinition.ObjectiveType.STRUCTURE_VISIT, "{\"structure\":\"minecraft:desert_pyramid\"}"),
            Map.entry(QuestDefinition.ObjectiveType.LOCATION_VISIT, "{\"location\":{\"pos\":[0,64,0]}}"),
            Map.entry(QuestDefinition.ObjectiveType.ITEM_CHECK, "{\"item\":\"minecraft:bread\",\"count\":2}"),
            Map.entry(QuestDefinition.ObjectiveType.MOB_KILL, "{\"entity\":\"minecraft:cow\"}"),
            Map.entry(QuestDefinition.ObjectiveType.BLOCK_BREAK, "{\"block\":\"minecraft:stone\"}"),
            Map.entry(QuestDefinition.ObjectiveType.BLOCK_PLACE, "{\"block\":\"minecraft:stone\"}"),
            Map.entry(QuestDefinition.ObjectiveType.BLOCK_INTERACT, "{\"block\":\"minecraft:bell\"}"),
            Map.entry(QuestDefinition.ObjectiveType.MEMORY_EVENT, "{\"memory\":\"villagerretaliation:gift_received\"}"),
            Map.entry(QuestDefinition.ObjectiveType.TRADE, "{\"item\":\"minecraft:diamond\"}"),
            Map.entry(QuestDefinition.ObjectiveType.GIFT, "{\"item\":\"minecraft:bread\"}"),
            Map.entry(QuestDefinition.ObjectiveType.REPUTATION, "{\"min\":20}"),
            Map.entry(QuestDefinition.ObjectiveType.CHOICE, "{\"key\":\"route\",\"value\":\"left\"}"),
            Map.entry(QuestDefinition.ObjectiveType.FACT, "{\"key\":\"route\",\"value\":\"left\"}"),
            Map.entry(QuestDefinition.ObjectiveType.CRITERION, "{\"criterion\":\"test:evidence\",\"match\":{\"source\":\"archive\"}}"),
            Map.entry(QuestDefinition.ObjectiveType.CAMPAIGN_OUTCOME, "{\"story\":{\"milestone\":\"decision\",\"outcome\":\"help\",\"scope\":\"completed\"}}"),
            Map.entry(QuestDefinition.ObjectiveType.CAMPAIGN_SPEAK, "{\"story\":{\"role\":\"mentor\",\"conversation\":\"test:talk\",\"node\":\"start\"}}"),
            Map.entry(QuestDefinition.ObjectiveType.CAMPAIGN_INVESTIGATE, "{\"story\":{\"evidence\":\"ledger\"}}"),
            Map.entry(QuestDefinition.ObjectiveType.CAMPAIGN_FOLLOW, "{\"story\":{\"role\":\"mentor\",\"location\":\"home\",\"scene\":\"test:walk\",\"step\":\"walk\"}}"),
            Map.entry(QuestDefinition.ObjectiveType.FOLLOWER_ARRIVAL, "{\"match\":{\"actor\":\"scout\"},\"location\":{\"pos\":[0,64,0]}}"),
            Map.entry(QuestDefinition.ObjectiveType.CONDITION, "{\"timer\":{\"mode\":\"duration\",\"duration_ticks\":20}}"));

    private QuestPlaytestObjectiveGameTests() { }

    @GameTest(template = "empty", batch = "playtest_type_matrix")
    public static void everyRegisteredObjectiveAndAliasHasALiveDiagnosticAndTestInstructions(GameTestHelper helper) throws Exception {
        helper.assertValueEqual(OBJECTIVES.size(), QuestDefinition.ObjectiveType.values().length, "the matrix must cover every objective type");
        for (var descriptor : QuestObjectiveRegistry.descriptors()) {
            var objective = json(OBJECTIVES.get(descriptor.objectiveType()));
            objective.addProperty("type", descriptor.id());
            try (var fixture = new Fixture(helper, objective)) {
                var row = VillagerQuestService.debugPlaytestView(fixture.player, fixture.quest.id()).objectives().getFirst();
                helper.assertValueEqual(row.type(), descriptor.id(), "live view uses the registered objective type");
                helper.assertFalse(row.guidance().isBlank(), "every type explains how to test it");
                helper.assertFalse(row.reason().equals("No evaluator"), "every type runs its real evaluator");
                var controls = QuestPlaytestSession.require(fixture.player, fixture.quest.id());
                controls.force("work", fixture.objective().id());
                helper.assertTrue(VillagerQuestService.debugPlaytestView(fixture.player, fixture.quest.id()).objectives().getFirst().bypassed(),
                        "forced objectives are explicitly distinguished from verified gameplay");
                controls.clear();
                if (descriptor.objectiveType().name().startsWith("CAMPAIGN_"))
                    helper.assertFalse(row.complete(), "missing campaign evidence is never treated as completed");
                for (String alias : descriptor.aliases()) {
                    var aliased = objective.deepCopy();
                    aliased.addProperty("type", alias);
                    helper.assertValueEqual(compile(helper, aliased, null).asQuestDefinition().objectives().getFirst().type(),
                            descriptor.objectiveType(), "aliases keep the same playtest behavior");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_items")
    public static void suppliesSatisfyComponentsDurabilityAndLargeCountsWithoutBypasses(GameTestHelper helper) throws Exception {
        for (String objective : List.of(
                "{\"type\":\"item_check\",\"item\":\"minecraft:iron_sword\",\"components\":{\"minecraft:damage\":{\"min\":10,\"max\":20}}}",
                "{\"type\":\"item_check\",\"item\":\"minecraft:bread\",\"components\":{\"minecraft:repair_cost\":{\"min\":3,\"max\":5}}}",
                "{\"type\":\"item_check\",\"item\":\"minecraft:bread\",\"custom_data\":{\"test\":\"supplies\"},\"count\":3}",
                "{\"type\":\"item_check\",\"item\":\"minecraft:bread\",\"count\":5000}")) {
            try (var fixture = new Fixture(helper, json(objective))) {
                var context = VillagerQuestService.debugPlaytestContext(fixture.player, fixture.quest.id());
                var definition = fixture.objective();
                var sample = QuestPlaytestSetup.sample(context, definition);
                helper.assertTrue(context.matchesItem(definition, sample), "constructed sample matches all authored constraints");
                QuestPlaytestSetup.prepare(context, definition, "");
                helper.assertFalse(QuestPlaytestSession.forced(fixture.player, fixture.quest.id(), "work", definition.id()),
                        "supplying items never records a forced completion");
                if (definition.count() < 100) {
                    helper.assertTrue(VillagerQuestService.debugPlaytestView(fixture.player, fixture.quest.id()).objectives().getFirst().complete(),
                            "the real inventory evaluator accepts the supplies");
                    int before = fixture.player.getInventory().countItem(sample.getItem());
                    QuestPlaytestSetup.prepare(context, definition, "");
                    helper.assertValueEqual(fixture.player.getInventory().countItem(sample.getItem()), before, "repeated setup gives only missing supplies");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_trade")
    public static void tradeSetupProvidesPaymentAndRequiresTheRealTradeEvent(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, json("{\"type\":\"trade\",\"item\":\"minecraft:diamond\",\"count\":2}"))) {
            var context = VillagerQuestService.debugPlaytestContext(fixture.player, fixture.quest.id());
            QuestPlaytestSetup.prepare(context, fixture.objective(), "");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.DIAMOND), 0, "setup does not give trade outputs");
            helper.assertTrue(fixture.player.getInventory().countItem(Items.EMERALD) >= 2, "setup supplies payment");
            var offer = fixture.provider.getOffers().stream().filter(value -> value.getResult().is(Items.DIAMOND)).findFirst().orElseThrow();
            helper.assertValueEqual(fixture.progress().objectiveCounter(fixture.objective().id()), 0, "setup is not a trade event");
            VillagerQuestService.onTradeCompleted(helper.getLevel(), fixture.player, fixture.provider, offer);
            helper.assertValueEqual(fixture.progress().objectiveCounter(fixture.objective().id()), 1, "one real trade event records one trade");
            QuestPlaytestSetup.prepare(context, fixture.objective(), "");
            helper.assertValueEqual(fixture.provider.getOffers().stream().filter(value -> value.getResult().is(Items.DIAMOND)).count(), 1L,
                    "repeated setup reuses the test offer");
            VillagerQuestService.onTradeCompleted(helper.getLevel(), fixture.player, fixture.provider, offer);
            helper.assertTrue(fixture.progress().objectiveComplete(fixture.objective().id()), "the requested trades complete naturally");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_world")
    public static void blocksMobsFactsAndReputationUseRealRuntimeChecks(GameTestHelper helper) throws Exception {
        for (var type : List.of(QuestDefinition.ObjectiveType.BLOCK_BREAK, QuestDefinition.ObjectiveType.BLOCK_PLACE,
                QuestDefinition.ObjectiveType.BLOCK_INTERACT, QuestDefinition.ObjectiveType.MOB_KILL,
                QuestDefinition.ObjectiveType.FACT, QuestDefinition.ObjectiveType.REPUTATION, QuestDefinition.ObjectiveType.GIFT)) {
            var objective = json(OBJECTIVES.get(type));
            objective.addProperty("type", type.name().toLowerCase(java.util.Locale.ROOT));
            try (var fixture = new Fixture(helper, objective)) {
                var context = VillagerQuestService.debugPlaytestContext(fixture.player, fixture.quest.id());
                var position = fixture.player.blockPosition().offset(2, 0, 0);
                helper.getLevel().setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                QuestPlaytestSetup.prepare(context, fixture.objective(), "");
                switch (type) {
                    case BLOCK_BREAK -> VillagerQuestService.onBlockBroken(helper.getLevel(), fixture.player, position, helper.getLevel().getBlockState(position));
                    case BLOCK_INTERACT -> VillagerQuestService.onBlockInteracted(helper.getLevel(), fixture.player, position, helper.getLevel().getBlockState(position));
                    case BLOCK_PLACE -> {
                        helper.assertTrue(fixture.player.getInventory().countItem(Items.STONE) > 0, "block placement gets an item");
                        VillagerQuestService.onBlockPlaced(helper.getLevel(), fixture.player, position, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    }
                    case MOB_KILL -> {
                        var cow = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,
                                fixture.player.getBoundingBox().inflate(8)).getFirst();
                        VillagerQuestService.onEntityKilled(cow, fixture.player);
                        cow.discard();
                    }
                    case GIFT -> VillagerQuestService.onGiftGiven(helper.getLevel(), fixture.player, fixture.provider,
                            new ItemStack(Items.BREAD), com.jvn.villagerretaliation.interaction.VillagerGiftPreferences.GiftReaction.NEUTRAL, 0);
                    default -> { }
                }
                helper.assertTrue(VillagerQuestService.debugPlaytestView(fixture.player, fixture.quest.id()).objectives().getFirst().complete(),
                        type + " completes through the runtime evaluator");
                helper.assertFalse(QuestPlaytestSession.forced(fixture.player, fixture.quest.id(), "work", fixture.objective().id()),
                        "setup and event dispatch do not create debug bypasses");
                helper.getLevel().setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_discovery")
    public static void discoveryBooksAndItemsStartWithoutProvidersAndRestoreBaselines(GameTestHelper helper) throws Exception {
        for (String event : List.of("item_acquired", "item_used", "book_opened")) for (String mode : List.of("automatic", "offer")) {
            var discovery = json("{\"id\":\"source\",\"event\":\"" + event + "\",\"mode\":\"" + mode + "\",\"match\":{\"book\":\"test:letter\"}}");
            try (var fixture = new Fixture(helper, json("{\"type\":\"item_check\",\"item\":\"minecraft:diamond\"}"), discovery)) {
                QuestPlaytestBaseline.capture(fixture.player, fixture.quest.id(), "Before discovery");
                QuestPlaytestDiscovery.prepare(fixture.player, fixture.quest.id(), "source", false);
                var book = fixture.player.getInventory().items.stream().filter(value -> value.is(Items.WRITTEN_BOOK)).findFirst().orElseThrow();
                helper.assertTrue(book.has(DataComponents.WRITTEN_BOOK_CONTENT), "test books can open in the real game UI");
                switch (event) {
                    case "item_acquired" -> QuestDiscoveryService.tick(fixture.player);
                    case "item_used" -> QuestDiscoveryService.use(fixture.player, book);
                    default -> fixture.player.openItemGui(book, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                if (mode.equals("offer")) {
                    helper.assertTrue(fixture.progress() == null, "discovery offers wait for real acceptance");
                    helper.assertTrue(QuestDiscoveryService.accept(fixture.player, fixture.quest.id()), "the normal journal acceptance path succeeds");
                }
                helper.assertTrue(fixture.progress() != null && fixture.progress().startedVillagerId() == null, "discovery starts without a fabricated provider");
                QuestPlaytestBaseline.restore(fixture.player, fixture.quest.id(), "Before discovery");
                helper.assertTrue(fixture.progress() == null, "providerless baseline restores the pre-discovery state");
                helper.assertFalse(QuestDiscoverySavedData.get(helper.getLevel()).offers(fixture.player.getUUID()).containsKey(fixture.quest.id()),
                        "providerless baseline clears later discovery offers");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_events")
    public static void criteriaAndMemoryRequireMatchingRealEvents(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper, json("{\"type\":\"criterion\",\"criterion\":\"test:evidence\",\"match\":{\"source\":\"archive\"},\"count\":2}"))) {
            var context = VillagerQuestService.debugPlaytestContext(fixture.player, fixture.quest.id());
            com.jvn.villagerretaliation.api.QuestCriterionApi.trigger(fixture.player, ResourceLocation.parse("test:evidence"),
                    Map.of("source", "wrong"), ItemStack.EMPTY, null);
            helper.assertValueEqual(fixture.progress().objectiveCounter(fixture.objective().id()), 0, "mismatched event data does not advance the quest");
            QuestPlaytestSetup.simulateCriterion(context, fixture.objective());
            helper.assertValueEqual(fixture.progress().objectiveCounter(fixture.objective().id()), 1, "simulation emits exactly one matching API event");
            helper.assertFalse(fixture.progress().objectiveComplete(fixture.objective().id()), "the event count still applies");
            QuestPlaytestSetup.simulateCriterion(context, fixture.objective());
            helper.assertTrue(fixture.progress().objectiveComplete(fixture.objective().id()), "the normal criterion listener completes");
            helper.assertFalse(QuestPlaytestSession.forced(fixture.player, fixture.quest.id(), "work", fixture.objective().id()), "API events never set bypass flags");
        }
        try (var fixture = new Fixture(helper, json("{\"type\":\"memory_event\",\"memory\":\"villagerretaliation:gift_received\"}"))) {
            for (String tag : List.of("test:unrelated", "villagerretaliation:gift_received")) {
                VillagerQuestService.onMemoryEvent(helper.getLevel(), new com.jvn.villagerretaliation.village.VillageEventMemory.MemoryEvent(
                        null, ResourceLocation.parse(tag), helper.getLevel().getGameTime(), helper.getLevel().dimension(),
                        fixture.player.blockPosition(), fixture.provider.getUUID(), fixture.player.getUUID(), null, null, null, null, null));
                helper.assertValueEqual(fixture.progress().objectiveComplete(fixture.objective().id()), tag.endsWith("gift_received"),
                        "memory filters remain active during playtesting");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_travel")
    public static void locationTravelKeepsAuthoredHeightAndRejectsBlockedDestinations(GameTestHelper helper) throws Exception {
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
        var objective = json("{\"type\":\"location_visit\",\"location\":{\"pos\":[0,64,0],\"radius\":2}}");
        objective.getAsJsonObject("location").add("pos", JsonParser.parseString("[" + position.getX() + "," + position.getY() + "," + position.getZ() + "]"));
        helper.getLevel().setBlock(position.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(position.above(4), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        try (var fixture = new Fixture(helper, objective)) {
            QuestPlaytestSetup.travel(VillagerQuestService.debugPlaytestContext(fixture.player, fixture.quest.id()), fixture.objective());
            helper.assertValueEqual(fixture.player.blockPosition(), position, "travel stays at the authored height below the roof");
            helper.assertTrue(VillagerQuestService.debugPlaytestView(fixture.player, fixture.quest.id()).objectives().getFirst().complete(),
                    "normal location checks accept the arrival");
            var before = fixture.player.position();
            boolean rejected = false;
            try {
                QuestPlaytestSetup.teleport(fixture.player, helper.getLevel(), new net.minecraft.core.BlockPos(position.getX(), -1000, position.getZ()));
            } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "travel outside world bounds is rejected");
            helper.assertValueEqual(fixture.player.position(), before, "failed travel never moves the player");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_reload")
    public static void editedStructureTargetsInvalidateOnlyChangedCachedDestinations(GameTestHelper helper) throws Exception {
        var objective = json("{\"type\":\"structure_visit\",\"structure\":\"minecraft:desert_pyramid\"}");
        try (var fixture = new Fixture(helper, objective)) {
            var controls = QuestPlaytestSession.require(fixture.player, fixture.quest.id());
            var target = new com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.LocatedTarget(
                    helper.getLevel().dimension(), fixture.player.blockPosition(), fixture.objective().id());
            controls.target(target);
            VillagerQuestResources.installCompiledTestCatalog(fixture.player.server, List.of(compile(helper, objective, null)));
            helper.assertValueEqual(QuestPlaytestSession.require(fixture.player, fixture.quest.id()).target(fixture.objective().id()), target,
                    "equivalent reloads retain prepared destinations");
            objective.addProperty("structure", "minecraft:igloo");
            VillagerQuestResources.installCompiledTestCatalog(fixture.player.server, List.of(compile(helper, objective, null)));
            helper.assertTrue(QuestPlaytestSession.require(fixture.player, fixture.quest.id()).target(fixture.objective().id()) == null,
                    "changed structure requirements cannot reuse a stale test destination");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "playtest_discovery_events")
    public static void everyEventDiscoveryRouteSupportsAutomaticAndJournalStarts(GameTestHelper helper) throws Exception {
        for (String event : List.of("mob_kill", "block_break", "block_place", "block_interact", "memory_event", "gift", "trade", "reputation", "criterion"))
            for (String mode : List.of("automatic", "offer")) {
                var discovery = json("{\"id\":\"source\",\"event\":\"" + event + "\",\"mode\":\"" + mode + "\"}");
                try (var fixture = new Fixture(helper, json("{\"type\":\"item_check\",\"item\":\"minecraft:diamond\"}"), discovery)) {
                    var kind = com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind.valueOf(event.toUpperCase(java.util.Locale.ROOT));
                    var memory = new com.jvn.villagerretaliation.village.VillageEventMemory.MemoryEvent(null,
                            ResourceLocation.parse("villagerretaliation:gift_received"), helper.getLevel().getGameTime(),
                            helper.getLevel().dimension(), fixture.player.blockPosition(), fixture.provider.getUUID(), fixture.player.getUUID(),
                            null, null, null, null, null);
                    var offer = new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD),
                            new ItemStack(Items.BREAD), 10, 0, 0);
                    var trigger = new com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent(kind, fixture.provider,
                            fixture.player.blockPosition(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), memory,
                            new ItemStack(Items.BREAD), com.jvn.villagerretaliation.interaction.VillagerGiftPreferences.GiftReaction.NEUTRAL,
                            fixture.provider, offer, 20, ResourceLocation.parse("test:evidence"), Map.of());
                    QuestDiscoveryService.event(fixture.player, trigger);
                    if (mode.equals("offer")) {
                        helper.assertTrue(fixture.progress() == null, event + " waits for journal acceptance");
                        helper.assertTrue(QuestDiscoveryService.accept(fixture.player, fixture.quest.id()), event + " journal offer can be accepted");
                    }
                    helper.assertTrue(fixture.progress() != null && fixture.progress().startedVillagerId() == null,
                            event + " starts with the authored discovery provider");
                }
            }
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
        helper.getLevel().setBlock(position.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        for (String mode : List.of("automatic", "offer")) {
            var discovery = json("{\"id\":\"source\",\"event\":\"location\",\"mode\":\"" + mode + "\",\"match\":{\"pos\":["
                    + position.getX() + "," + position.getY() + "," + position.getZ() + "],\"radius\":2}}");
            try (var fixture = new Fixture(helper, json("{\"type\":\"item_check\",\"item\":\"minecraft:diamond\"}"), discovery)) {
                QuestPlaytestDiscovery.prepare(fixture.player, fixture.quest.id(), "source", true);
                fixture.player.tickCount = 20;
                QuestDiscoveryService.tick(fixture.player);
                if (mode.equals("offer")) helper.assertTrue(QuestDiscoveryService.accept(fixture.player, fixture.quest.id()), "location offer can be accepted");
                helper.assertTrue(fixture.progress() != null && fixture.progress().startedVillagerId() == null, "location starts without a provider");
            }
        }
        helper.succeed();
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    private static CompiledQuest compile(GameTestHelper helper, JsonObject objective, JsonObject discovery) {
        var root = json("""
                {"schema":"villagerretaliation:quest/v2","id":"test:playtest_matrix",
                 "provider":{"type":"villagerretaliation:villager"},"metadata":{"title":"Playtest matrix"},
                 "availability":{"max_starts":0,"max_completions":0},"entry_stage":"work",
                 "stages":[{"id":"work","objectives":[],"next":"return"},{"id":"return","objectives":[]}]}
                """);
        objective = objective.deepCopy();
        objective.addProperty("id", "requirement");
        root.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("objectives").add(objective);
        if (objective.has("story")) root.getAsJsonObject("metadata").addProperty("campaign", "test:campaign");
        if (discovery != null) {
            root.getAsJsonObject("provider").addProperty("type", "villagerretaliation:discovery");
            var routes = new com.google.gson.JsonArray(); routes.add(discovery); root.add("discovery", routes);
        }
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/matrix/quest.json"), root).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(),
                envelope, helper.getLevel().registryAccess()).orElseThrow();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final net.minecraft.server.level.ServerPlayer player;
        final net.minecraft.world.entity.npc.Villager provider;
        final CompiledQuest quest;
        final AutoCloseable restore;
        final List<String> selected;

        Fixture(GameTestHelper helper, JsonObject objective) throws Exception { this(helper, objective, null); }
        Fixture(GameTestHelper helper, JsonObject objective, JsonObject discovery) throws Exception {
            this.helper = helper;
            quest = compile(helper, objective, discovery);
            var server = helper.getLevel().getServer();
            selected = List.copyOf(server.getPackRepository().getSelectedIds());
            var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/test_content");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Playtest fixture\"}}");
            server.getPackRepository().reload();
            var packs = new java.util.ArrayList<>(selected);
            if (!packs.contains("file/test_content")) packs.add("file/test_content");
            server.getPackRepository().setSelected(packs);
            restore = VillagerQuestResources.preserveTestOverride();
            VillagerQuestResources.installCompiledTestCatalog(server, List.of(quest));
            player = helper.makeMockServerPlayerInLevel();
            provider = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
            provider.setNoAi(true);
            player.teleportTo(provider.getX() - 1, provider.getY(), provider.getZ());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            if (discovery != null) QuestPlaytestSession.bindDiscovery(player, quest.id(), true);
            else {
                QuestPlaytestSession.bind(player, quest.id(), provider, true);
                var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(player.getUUID(), quest.id());
                progress.start(provider.getUUID(), helper.getLevel().dimension(), null, helper.getLevel().getGameTime());
                progress.setCurrentStage("work");
            }
        }

        QuestDefinition.Objective objective() { return quest.asQuestDefinition().objectives().getFirst(); }
        VillagerQuestSavedData.QuestProgress progress() { return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), quest.id()); }

        public void close() throws Exception {
            QuestPlaytestSession.close(player.server, player.getUUID());
            QuestPlaytestBaseline.clear(player.server, player.getUUID());
            VillagerQuestSavedData.get(helper.getLevel()).remove(player.getUUID(), quest.id());
            QuestDiscoverySavedData.get(helper.getLevel()).remove(player.getUUID(), quest.id());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestService.clearRuntimeState(player);
            player.server.getPackRepository().setSelected(selected);
            provider.discard();
            restore.close();
        }
    }
}
