package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog;
import com.jvn.villagerretaliation.quest.content.*;
import com.jvn.villagerretaliation.quest.discovery.*;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_discovery")
@PrefixGameTestTemplate(false)
public final class QuestDiscoveryGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("test:discovery");
    private QuestDiscoveryGameTests() { }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void readingMarkedBookStartsWithoutProviderAndDoesNotReplay(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "book_opened", "automatic")) {
            var book = book();
            var before = book.copy();
            int xp = fixture.player.totalExperience;
            fixture.player.openItemGui(book, net.minecraft.world.InteractionHand.MAIN_HAND);
            var progress = fixture.progress();
            helper.assertTrue(progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE, "book starts quest");
            helper.assertTrue(progress.startedVillagerId() == null, "discovery has no fabricated villager");
            var context = QuestExecutionContext.fromDialogueContext(DialogueContext.forPlayer(fixture.player),
                    VillagerQuestResources.quest(helper.getLevel().getServer(), ID).orElseThrow(), "book_opened");
            helper.assertValueEqual(context.providerType().id(), context.binding().providerType(), "discovery provider type matches its binding");
            helper.assertValueEqual(fixture.player.totalExperience - xp, 7, "entry effects run");
            fixture.player.openItemGui(book, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertValueEqual(progress.startCount(), 1, "repeated opening cannot restart");
            helper.assertValueEqual(fixture.player.totalExperience - xp, 7, "entry effects do not replay");
            helper.assertTrue(ItemStack.matches(book, before), "book remains unchanged");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void ordinaryBookDoesNotEnroll(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "book_opened", "automatic")) {
            QuestDiscoveryService.bookOpened(fixture.player, new ItemStack(Items.WRITTEN_BOOK));
            helper.assertTrue(fixture.progress() == null, "ordinary book has no quest marker");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void acquiredBookCreatesPersistentOfferAndAcceptsOnce(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "item_acquired", "offer")) {
            fixture.player.getInventory().setItem(0, book());
            QuestDiscoveryService.tick(fixture.player);
            var data = QuestDiscoverySavedData.get(helper.getLevel());
            helper.assertValueEqual(data.offers(fixture.player.getUUID()).size(), 1, "one offer");
            helper.assertTrue(fixture.progress() == null, "offer does not start quest");
            var restored = QuestDiscoverySavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            var reloaded = QuestDiscoverySavedData.load(restored, helper.getLevel().registryAccess());
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_discoveries", reloaded);
            helper.assertTrue(reloaded.offers(fixture.player.getUUID()).size() == 1, "offer persists");
            helper.assertTrue(QuestDiscoveryService.accept(fixture.player, ID), "offer accepts");
            helper.assertTrue(fixture.progress().state() == VillagerQuestSavedData.QuestState.ACTIVE, "accepted quest active");
            helper.assertFalse(QuestDiscoveryService.accept(fixture.player, ID), "offer cannot accept twice");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void playerOnlyStagesAdvanceAndAwardCompletion(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "book_opened", "automatic")) {
            QuestDiscoveryService.bookOpened(fixture.player, book());
            fixture.player.getInventory().setItem(1, new ItemStack(Items.DIAMOND));
            fixture.progress().markObjectiveComplete("travel.proof");
            int before = fixture.player.totalExperience;
            VillagerQuestService.onPlayerTick(fixture.player);
            var context = DialogueContext.forPlayer(fixture.player);
            var outcome = VillagerQuestService.performAction(context, ID,
                    com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
            helper.assertValueEqual(outcome.status(), "completed", "player-only completion works");
            helper.assertValueEqual(fixture.player.totalExperience - before, 3, "completion reward delivered");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void escortCommandsPersistAndReleaseWithoutTaming(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "book_opened", "automatic")) {
            QuestDiscoveryService.bookOpened(fixture.player, book());
            var wolf = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, new net.minecraft.core.BlockPos(1, 2, 1));
            fixture.player.teleportTo(wolf.getX() + 1, wolf.getY(), wolf.getZ());
            var action = follower(wolf, "follow", true);
            var context = DialogueContext.forPlayer(fixture.player);
            helper.assertTrue(com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(context, action, Map.of()).success(), "escort starts");
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(wolf), "quest controls navigation");
            helper.assertFalse(wolf.isTame(), "escort does not tame the dog");
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.protects(wolf), "authored protection enabled");
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.command(fixture.player, wolf.getUUID(), false), "participant can wait");
            var data = com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData.get(helper.getLevel());
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getList("Followers", 10).getCompound(0).getString("Command"), "wait", "manual override saved");
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_followers",
                    com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData.load(saved, helper.getLevel().registryAccess()));
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(wolf), "escort survives reload");
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.arrived(helper.getLevel(), fixture.player.getUUID(), ID,
                    "companion", wolf.blockPosition(), helper.getLevel().dimension(), 2), "arrival uses NPC position");
            helper.assertFalse(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.arrived(helper.getLevel(), fixture.player.getUUID(), ID,
                    "companion", wolf.blockPosition(), net.minecraft.world.level.Level.NETHER, 2), "arrival checks dimension");
            com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(context, follower(wolf, "resume", false), Map.of());
            saved = com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getList("Followers", 10).getCompound(0).getString("Command"), "follow", "authored directive replaces manual wait");
            VillagerQuestService.performAction(context, ID, com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.FAIL);
            helper.assertFalse(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.retained(wolf), "terminal quest releases escort");
            helper.assertFalse(wolf.isTame(), "released dog keeps original ownership");
            wolf.discard();
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void escortRejectsAnotherPlayersPet(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "book_opened", "automatic")) {
            QuestDiscoveryService.bookOpened(fixture.player, book());
            var wolf = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, new net.minecraft.core.BlockPos(1, 2, 1));
            wolf.setTame(true, false);
            wolf.setOwnerUUID(java.util.UUID.randomUUID());
            var owner = wolf.getOwnerUUID();
            helper.assertFalse(com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(DialogueContext.forPlayer(fixture.player),
                    follower(wolf, "follow", false), Map.of()).success(), "other player's pet cannot be commandeered");
            helper.assertValueEqual(wolf.getOwnerUUID(), owner, "ownership preserved");
            wolf.discard();
            helper.succeed();
        }
    }

    private static com.jvn.villagerretaliation.action.VillagerActionDefinition follower(net.minecraft.world.entity.Entity actor, String command, boolean protect) {
        var raw = JsonParser.parseString("{\"type\":\"follower\",\"command\":\"" + command + "\",\"protected\":" + protect
                + ",\"actor\":{\"alias\":\"companion\",\"type\":\"villagerretaliation:living_entity\",\"binding_source\":\"uuid\",\"binding\":\"" + actor.getUUID() + "\"}}").getAsJsonObject();
        return com.jvn.villagerretaliation.action.VillagerActionDefinition.readObjects(ID, "test", List.of(raw), ID).getFirst();
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void locationDiscoveryChecksDimensionAndRadius(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "location", "automatic", root -> {
            var match = new com.google.gson.JsonObject();
            match.add("pos", JsonParser.parseString("[100,64,100]"));
            match.addProperty("radius", 4);
            match.addProperty("dimension", "minecraft:overworld");
            root.getAsJsonArray("discovery").get(0).getAsJsonObject().add("match", match);
        })) {
            fixture.player.tickCount = 20;
            fixture.player.teleportTo(120, 64, 100);
            QuestDiscoveryService.tick(fixture.player);
            helper.assertTrue(fixture.progress() == null, "outside radius ignored");
            fixture.player.teleportTo(102, 64, 100);
            QuestDiscoveryService.tick(fixture.player);
            helper.assertTrue(fixture.progress() != null, "entering discovery area starts quest");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void eventOfferPreservesPayloadConditionsThroughReload(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "block_interact", "offer", root -> {
            var entry = root.getAsJsonArray("discovery").get(0).getAsJsonObject();
            entry.add("match", JsonParser.parseString("{\"payload\":{\"block\":\"minecraft:bell\"}}"));
            entry.add("conditions", JsonParser.parseString("[{\"type\":\"all\",\"conditions\":[{\"type\":\"trigger_payload\",\"block\":\"minecraft:bell\"}]}]"));
        })) {
            var event = com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent.block(
                    com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind.BLOCK_INTERACT,
                    fixture.player.blockPosition(), net.minecraft.world.level.block.Blocks.BELL.defaultBlockState());
            QuestDiscoveryService.event(fixture.player, event);
            var data = QuestDiscoverySavedData.get(helper.getLevel());
            helper.assertValueEqual(data.offers(fixture.player.getUUID()).size(), 1, "matching gameplay event creates offer");
            var restored = QuestDiscoverySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.getLevel().getServer().overworld().getDataStorage().set("villagerretaliation_discoveries", restored);
            helper.assertTrue(QuestDiscoveryService.accept(fixture.player, ID), "saved event conditions accept later");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "quest_discovery_navigation", timeoutTicks = 180)
    public static void escortWalksToPlayerAndHoldsManualWait(GameTestHelper helper) {
        var fixture = new Fixture(helper, "book_opened", "automatic");
        QuestDiscoveryService.bookOpened(fixture.player, book());
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 3; z++)
            helper.setBlock(new net.minecraft.core.BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
        var wolf = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, new net.minecraft.core.BlockPos(1, 2, 1));
        var target = helper.absolutePos(new net.minecraft.core.BlockPos(8, 2, 1));
        fixture.player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        var result = com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(DialogueContext.forPlayer(fixture.player), follower(wolf, "follow", false), Map.of());
        helper.assertTrue(result.success(), result.message());
        double initial = wolf.distanceToSqr(fixture.player);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(wolf.distanceToSqr(fixture.player) < initial - 4, "escort navigates toward player");
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.command(fixture.player, wolf.getUUID(), false), "wait accepted nearby");
            var anchor = wolf.position();
            fixture.player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 4.5);
            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertTrue(wolf.position().distanceToSqr(anchor) < 1, "manual wait holds NPC position");
                    helper.succeed();
                } finally { wolf.discard(); fixture.close(); }
            });
        });
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void providerFreeConfirmationPacketRoundTrips(GameTestHelper helper) {
        var payload = new com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload(java.util.UUID.randomUUID(), 1, -1, null, true, "Shared world consequences");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload.STREAM_CODEC.decode(buffer), payload, "provider-free confirmation packet");
            buffer.clear();
            var existing = new com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload(java.util.UUID.randomUUID(), 2, 7, java.util.UUID.randomUUID(), true, "Existing conversation");
            buffer.writeUUID(existing.requestId());
            buffer.writeVarLong(existing.revision());
            buffer.writeVarInt(existing.entityId());
            buffer.writeUUID(existing.providerId());
            buffer.writeBoolean(existing.pending());
            buffer.writeUtf(existing.text(), 8192);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload.STREAM_CODEC.decode(buffer), existing, "existing confirmation wire format preserved");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_discovery")
    public static void escortDestinationTracksMovingNpcAndCompletesAutomatically(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, "book_opened", "automatic", root -> {
            var stage = root.getAsJsonArray("stages").get(0).getAsJsonObject();
            stage.add("objectives", JsonParser.parseString("""
                    [{"id":"arrive","type":"follower_arrival","match":{"actor":"companion","destination_tag":"test_receiver"},
                      "radius":2,"tracker":{"text":"Bring your companion to {destination_name} at {target_x}, {target_y}, {target_z}."}}]
                    """));
            stage.add("next", JsonParser.parseString("{\"complete\":true}"));
        })) {
            QuestDiscoveryService.bookOpened(fixture.player, book());
            var wolf = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, new net.minecraft.core.BlockPos(1, 2, 1));
            var receiver = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new net.minecraft.core.BlockPos(2, 2, 1));
            receiver.addTag("test_receiver");
            receiver.setCustomName(net.minecraft.network.chat.Component.literal("Scout Arlo"));
            var definition = VillagerQuestResources.quest(helper.getLevel().getServer(), ID).orElseThrow();
            var objective = definition.objectives().getFirst();
            var context = new com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext(fixture.player,
                    DialogueContext.forPlayer(fixture.player), helper.getLevel(), definition, fixture.progress(),
                    null, null, null, null, null, null);
            var type = new com.jvn.villagerretaliation.quest.follower.QuestFollowerArrivalObjective();
            com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(DialogueContext.forPlayer(fixture.player), follower(wolf, "follow", false), Map.of());
            helper.assertTrue(type.evaluate(context, objective).complete(), "NPC is initially at the recipient");
            receiver.teleportTo(receiver.getX() + 8, receiver.getY(), receiver.getZ());
            helper.assertFalse(type.evaluate(context, objective).complete(), "arrival follows the moving recipient");
            VillagerQuestService.onPlayerTick(fixture.player);
            helper.assertValueEqual(fixture.progress().targetPos(), receiver.blockPosition(), "journal waypoint tracks recipient");
            var replacements = com.jvn.villagerretaliation.quest.tracking.QuestPresentationService.trackerReplacements(
                    fixture.player, definition, fixture.progress(), objective, true, true,
                    com.jvn.villagerretaliation.quest.tracking.QuestPresentationService.ObjectiveState.EMPTY);
            helper.assertValueEqual(replacements.get("destination_name"), "Scout Arlo", "tracker resolves recipient name");
            helper.assertValueEqual(replacements.get("target_x"), Integer.toString(receiver.blockPosition().getX()), "tracker resolves current coordinates");
            wolf.teleportTo(receiver.getX(), receiver.getY(), receiver.getZ());
            fixture.player.tickCount += 20;
            VillagerQuestService.onPlayerTick(fixture.player);
            helper.assertValueEqual(fixture.progress().state(), VillagerQuestSavedData.QuestState.COMPLETED, "terminal stage completes without NPC dialogue");
            helper.assertFalse(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.retained(wolf), "automatic completion releases escort");
            receiver.discard();
            wolf.discard();
            helper.succeed();
        }
    }

    private static ItemStack book() {
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        var tag = new CompoundTag();
        tag.putString("villagerretaliation:quest_book", "test:letter");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final QuestContentCatalog previous;
        private final QuestContentLoadReport report;
        private final VillagerQuestSavedData previousQuests;
        private final QuestDiscoverySavedData previousDiscoveries;
        private final ServerPlayer player;
        private final com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData previousFollowers;

        private Fixture(GameTestHelper helper, String event, String mode) {
            this(helper, event, mode, root -> { });
        }

        private Fixture(GameTestHelper helper, String event, String mode, java.util.function.Consumer<com.google.gson.JsonObject> customize) {
            this.helper = helper;
            var server = helper.getLevel().getServer();
            previous = QuestContentCatalogs.current(server);
            report = QuestContentCatalogs.loadReport(server);
            previousQuests = VillagerQuestSavedData.get(helper.getLevel());
            previousDiscoveries = QuestDiscoverySavedData.get(helper.getLevel());
            previousFollowers = com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData.get(helper.getLevel());
            server.overworld().getDataStorage().set("villagerretaliation_followers", new com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData());
            server.overworld().getDataStorage().set("villagerretaliation_quests", new VillagerQuestSavedData());
            server.overworld().getDataStorage().set("villagerretaliation_discoveries", new QuestDiscoverySavedData());
            player = helper.makeMockServerPlayerInLevel();
            VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            var root = JsonParser.parseString("""
                    {"schema":"villagerretaliation:quest/v2","id":"test:discovery","localization_prefix":"test.discovery",
                     "provider":{"type":"villagerretaliation:discovery"},
                     "discovery":[{"id":"letter","event":"%s","mode":"%s","match":{"book":"test:letter"}}],
                     "entry_stage":"travel","stages":[{"id":"travel",
                      "on_enter":[{"type":"experience","amount":7}],
                      "objectives":[{"id":"proof","type":"item_check","item":"minecraft:diamond","count":1}]}],
                     "rewards":{"actions":[{"type":"experience","amount":3}]}}
                    """.formatted(event, mode)).getAsJsonObject();
            customize.accept(root);
            var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/test/discovery/quest.json"), root, new com.jvn.villagerretaliation.quest.schema.QuestResourceSource(ID, "test")).orElseThrow();
            CompiledQuest quest = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope, helper.getLevel().registryAccess()).orElseThrow();
            var compiled = new LinkedHashMap<ResourceLocation, CompiledQuest>();
            previous.compiledQuests().forEach(value -> compiled.put(value.id(), value));
            compiled.put(ID, quest);
            var definitions = new LinkedHashMap<>(previous.quests());
            definitions.put(ID, quest.asQuestDefinition());
            var replacement = new QuestContentCatalog(previous.generation(), new CompiledQuestCatalog(compiled), previous.dialogueCatalog(),
                    definitions, previous.objectiveEventQuestIds(), previous.factQuestIds(), previous.memoryEventQuestIds(),
                    previous.exclusiveGroupQuestIds(), previous.triggerEventQuestIds(), previous.scenes(), previous.encounters(), previous.pools(),
                    previous.bundles(), previous.localization(), previous.rewards());
            QuestContentCatalogs.installForTests(server, replacement, report);
        }

        private VillagerQuestSavedData.QuestProgress progress() {
            return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), ID);
        }

        @Override
        public void close() {
            var server = helper.getLevel().getServer();
            server.overworld().getDataStorage().set("villagerretaliation_quests", previousQuests);
            server.overworld().getDataStorage().set("villagerretaliation_discoveries", previousDiscoveries);
            server.overworld().getDataStorage().set("villagerretaliation_followers", previousFollowers);
            QuestContentCatalogs.installForTests(server, previous, report);
            VillagerQuestService.clearRuntimeState(player);
            player.discard();
        }
    }
}
