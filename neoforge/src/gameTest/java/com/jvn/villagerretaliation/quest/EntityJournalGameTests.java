package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.client.quest.QuestTrackingSelection;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_journal")
@PrefixGameTestTemplate(false)
public final class EntityJournalGameTests {
    private EntityJournalGameTests() { }

    @GameTest(template = "empty")
    public static void trackingUsesQuestIdentityAndOrderWithoutLiveVillagers(GameTestHelper helper) {
        var wolf = entry("test:wolf", "Wolf", "active");
        var cow = entry("test:cow", "Cow", "active");
        var completed = entry("test:done", "Keeper", "completed");
        var ids = List.of("test:cow", "test:missing", "test:wolf", "test:done");
        helper.assertValueEqual(QuestTrackingSelection.entries(List.of(wolf, completed, cow), ids), List.of(cow, wolf),
                "tracked entries retain player selection order and omit missing or terminal quests");
        helper.assertValueEqual(QuestTrackingSelection.index(cow, ids), 0, "tracking uses quest identity without an entity type");
        helper.assertValueEqual(QuestTrackingSelection.index(wolf, ids), 2, "saved tracking positions remain stable");
        helper.assertValueEqual(QuestTrackingSelection.index(completed, ids), -1, "terminal quests cannot be tracked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trackerModelsKeepUpdatesAndHistoryIndependentForEachConsumer(GameTestHelper helper) {
        var model = new com.jvn.villagerretaliation.client.quest.QuestTrackerModel();
        var other = new com.jvn.villagerretaliation.client.quest.QuestTrackerModel();
        var wolf = entry("test:wolf", "Wolf", "active").withQuestUpdate(true);
        var done = entry("test:done", "Cow", "completed");
        var entity = java.util.UUID.randomUUID();
        model.accept(List.of(wolf, done), List.of(wolf.questId()), List.of(), java.util.Map.of(wolf.questId(), entity));
        helper.assertTrue(model.hasIndicator(entity), "quest indicators use arbitrary entity identities");
        helper.assertValueEqual(model.trackedEntries(), List.of(wolf), "model tracks the selected provider quest");
        helper.assertTrue(other.entries().isEmpty(), "another consumer has independent tracker state");
        model.acknowledge(wolf);
        helper.assertFalse(model.hasIndicator(entity), "acknowledgement clears the entity indicator");
        helper.assertFalse(model.entries().getFirst().questUpdate(), "acknowledgement clears the quest update");
        model.accept(List.of(wolf.withQuestUpdate(false)), List.of(wolf.questId()), List.of(), java.util.Map.of());
        helper.assertTrue(model.entries().contains(done), "completed quest history survives a later payload");
        model.reset();
        helper.assertTrue(model.entries().isEmpty() && model.trackedEntries().isEmpty(), "reset retires all session data");
        model.accept(List.of(), List.of(), List.of(), java.util.Map.of());
        helper.assertTrue(model.entries().isEmpty(), "reset also clears completed quest history");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedJournalStateAcceptsDataWithoutPacketsOrWireLimits(GameTestHelper helper) {
        var model = new com.jvn.villagerretaliation.client.quest.QuestJournalModel();
        String title = "A long quest title ".repeat(20);
        var objectives = new java.util.ArrayList<com.jvn.villagerretaliation.quest.tracking.QuestJournalData.ObjectiveStep>();
        objectives.add(new com.jvn.villagerretaliation.quest.tracking.QuestJournalData.ObjectiveStep("Find the trail", false));
        var entry = new com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry(
                "other_mod:trail", title, "Find the trail", "", "", 0.5F, true, "active", "", "Scout", "",
                List.of(), List.of(), List.of(), objectives, true, false, null);
        objectives.clear();
        model.accept(List.of(entry), List.of(entry.questId()), List.of(), java.util.Map.of());
        helper.assertValueEqual(model.entries().getFirst().title(), title, "shared data is not cropped to a packet limit");
        helper.assertValueEqual(model.entries().getFirst().objectiveSteps().size(), 1, "shared data snapshots mutable source lists");
        helper.assertValueEqual(model.trackedEntries(), List.of(entry), "shared tracking accepts direct consumer data");
        model.acknowledge(entry);
        helper.assertFalse(model.entries().getFirst().questUpdate(), "shared update receipts do not require a packet record");
        var hidden = entry.withJournal(new com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Journal(
                "", List.of(), "", "", "", 0, true, -1, -1, null, "", 0, 0));
        model.accept(List.of(hidden), List.of(entry.questId()), List.of(), java.util.Map.of());
        helper.assertTrue(model.entries().isEmpty(), "hidden shared entries stay out of the journal and HUD");
        model.reset();
        helper.assertTrue(model.entries().isEmpty() && model.trackedEntries().isEmpty(), "shared session data resets independently");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vrAdapterRetainsEveryJournalFieldAndWireEncoding(GameTestHelper helper) {
        var item = new QuestTrackerSyncPayload.QuestItem("minecraft:iron_sword", "Proof", 2, 1,
                com.jvn.villagerretaliation.util.item.ItemStackPredicate.ANY,
                List.of(new QuestTrackerSyncPayload.QuestItemEnchantment("minecraft:sharpness", 1, 3)), 20, 80);
        var journal = new QuestTrackerSyncPayload.Journal("Trails", List.of("scouting", "urgent"), "minecraft:compass",
                "#abcdef", "#123456", 9, false, 1600, 1200,
                new QuestTrackerSyncPayload.Waypoint("minecraft:overworld", 10, 64, -20), "Wait for dusk", 2, 4);
        var entry = new QuestTrackerSyncPayload.Entry("test:trail", "Trail", "Find proof", "Description", "Metadata",
                0.5F, true, "active", "Return", "Scout", "Overworld", List.of(item),
                List.of(new QuestTrackerSyncPayload.RewardPreview("item", "Emeralds", 3, "minecraft:emerald")),
                List.of(new QuestTrackerSyncPayload.Prerequisite("test:start", "Start", true)),
                List.of(new QuestTrackerSyncPayload.ObjectiveStep("Follow tracks", true)), true, false, journal);
        var node = new QuestTrackerSyncPayload.QuestlineNode("test:trail", "Trail", "Description", "Trails",
                List.of("test:start"), "minecraft:compass", "#abcdef", "active");
        var shared = com.jvn.villagerretaliation.client.quest.VrQuestJournalData.shared(entry);
        helper.assertValueEqual(com.jvn.villagerretaliation.client.quest.VrQuestJournalData.legacy(shared), entry,
                "the VR adapter retains all entry, journal, reward, prerequisite, objective, and item fields");
        helper.assertValueEqual(com.jvn.villagerretaliation.client.quest.VrQuestJournalData.legacy(
                com.jvn.villagerretaliation.client.quest.VrQuestJournalData.shared(node)), node, "questline graph metadata survives adaptation");
        var payload = new QuestTrackerSyncPayload(List.of(entry), List.of(entry.questId()), true, List.of(node),
                List.of(new QuestTrackerSyncPayload.QuestMarker(java.util.UUID.randomUUID(), entry.questId())));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            QuestTrackerSyncPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(QuestTrackerSyncPayload.STREAM_CODEC.decode(buffer), payload, "VR wire data still round trips exactly");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void presentationUsesConsumerResourcesAndImmutableSettings(GameTestHelper helper) {
        var texture = net.minecraft.resources.ResourceLocation.parse("other_mod:textures/gui/journal.png");
        var textures = new java.util.EnumMap<com.jvn.villagerretaliation.client.quest.QuestPresentation.Texture, net.minecraft.resources.ResourceLocation>(
                com.jvn.villagerretaliation.client.quest.QuestPresentation.Texture.class);
        textures.put(com.jvn.villagerretaliation.client.quest.QuestPresentation.Texture.CONTAINER, texture);
        var colors = new java.util.EnumMap<com.jvn.villagerretaliation.client.quest.QuestPresentation.Color, Integer>(
                com.jvn.villagerretaliation.client.quest.QuestPresentation.Color.class);
        colors.put(com.jvn.villagerretaliation.client.quest.QuestPresentation.Color.TEXT_COLOR, 0xFF123456);
        var presentation = new com.jvn.villagerretaliation.client.quest.QuestPresentation(textures, colors, "other_mod.gui.", 2);
        textures.clear();
        colors.clear();
        helper.assertValueEqual(presentation.texture(com.jvn.villagerretaliation.client.quest.QuestPresentation.Texture.CONTAINER), texture,
                "the consumer supplies its own journal texture without VR resources");
        helper.assertValueEqual(presentation.color(com.jvn.villagerretaliation.client.quest.QuestPresentation.Color.TEXT_COLOR), 0xFF123456,
                "presentation snapshots the consumer's palette");
        helper.assertValueEqual(presentation.key("quest_journal.title"), "other_mod.gui.quest_journal.title",
                "the consumer owns its localization namespace");
        helper.assertValueEqual(presentation.maxHudEntries(), 2, "HUD presentation settings belong to the consumer");
        helper.succeed();
    }

    private static QuestTrackerSyncPayload.Entry entry(String id, String issuer, String state) {
        return new QuestTrackerSyncPayload.Entry(id, "A task from " + issuer, "Find the trail", "", 0.0F, false,
                state, "", issuer, "Overworld", List.of());
    }
}
