package com.jvn.villagerretaliation.dialogue.normal;

import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class DialogueTreeSessionGameTests {
    private DialogueTreeSessionGameTests() { }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void ordinaryDialogueRetainsItsCursorAndSubstitutionsAcrossReload(GameTestHelper helper) {
        var data = new DialogueTreeSessionSavedData();
        var key = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        var session = new DialogueTreeService.Session(UUID.randomUUID(), ResourceLocation.parse("fixture:lore"),
                "road", 10, Map.of("player", "Alex", "reward", "seven"));
        data.put(key, session);
        var restored = DialogueTreeSessionSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()));
        helper.assertValueEqual(restored.get(key), session, "ordinary session survives reload without replaying actions");
        restored.remove(key);
        helper.assertTrue(DialogueTreeSessionSavedData.load(restored.save(new CompoundTag(), helper.getLevel().registryAccess())).get(key) == null,
                "explicit departure removes the saved cursor");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void incompatibleDialogueTreeDataRemainsIntact(GameTestHelper helper) {
        var data = new DialogueTreeSessionSavedData();
        var key = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        var session = new DialogueTreeService.Session(UUID.randomUUID(), ResourceLocation.parse("fixture:lore"),
                "road", 10, Map.of("player", "Alex"));
        data.put(key, session);
        var future = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        future.putInt("DataVersion", 2);
        var restored = DialogueTreeSessionSavedData.load(future);
        helper.assertFalse(restored.diagnostic().isBlank(), "future data is unavailable");
        helper.assertValueEqual(restored.save(new CompoundTag(), helper.getLevel().registryAccess()), future,
                "unsupported data survives without losing saved sessions");
        future.putInt("DataVersion", 1);
        future.getList("Sessions", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("Player");
        restored = DialogueTreeSessionSavedData.load(future);
        helper.assertTrue(restored.diagnostic().isBlank(), "one malformed entry does not disable other conversations");
        helper.assertValueEqual(restored.save(new CompoundTag(), helper.getLevel().registryAccess()), future,
                "malformed data remains recoverable");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void malformedAndDuplicateSessionsAreIsolatedAndRetained(GameTestHelper helper) {
        var data = new DialogueTreeSessionSavedData();
        var healthy = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        var damaged = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        var duplicate = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        var session = new DialogueTreeService.Session(UUID.randomUUID(), ResourceLocation.parse("fixture:lore"), "road", 10, Map.of());
        data.put(healthy, session);
        data.put(damaged, session);
        data.put(duplicate, session);
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var entries = saved.getList("Sessions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        entries.getCompound(1).putInt("Touched", -1);
        var malformed = entries.getCompound(1).copy();
        var repeated = entries.getCompound(2).copy();
        entries.add(repeated.copy());
        var restored = DialogueTreeSessionSavedData.load(saved);
        helper.assertTrue(restored.diagnostic().isBlank(), "record errors do not create a world-wide block");
        helper.assertValueEqual(restored.get(healthy), session, "healthy cursor survives neighboring corruption");
        helper.assertFalse(restored.diagnostic(damaged).isBlank(), "damaged conversation remains blocked");
        helper.assertFalse(restored.diagnostic(duplicate).isBlank(), "ambiguous duplicate conversation remains blocked");
        helper.assertTrue(restored.get(damaged) == null && restored.get(duplicate) == null, "invalid sessions are never selected");
        restored.remove(healthy);
        var newcomer = new DialogueTreeService.SessionKey(UUID.randomUUID(), UUID.randomUUID());
        restored.put(newcomer, session);
        var rewritten = restored.save(new CompoundTag(), helper.getLevel().registryAccess());
        var retained = rewritten.getList("Sessions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        helper.assertValueEqual(retained.stream().filter(malformed::equals).count(), 1L, "malformed original is preserved");
        helper.assertValueEqual(retained.stream().filter(repeated::equals).count(), 2L, "both duplicate originals are preserved");
        var reloaded = DialogueTreeSessionSavedData.load(rewritten);
        helper.assertValueEqual(reloaded.get(newcomer), session, "healthy mutations survive another reload");
        helper.assertFalse(reloaded.diagnostic(damaged).isBlank() || reloaded.diagnostic(duplicate).isBlank(), "quarantine survives reload");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void missingDialogueContentRetainsProgressWithoutBlockingOtherTopics(GameTestHelper helper) {
        var level = helper.getLevel();
        var prior = DialogueTreeSessionSavedData.get(level);
        var data = new DialogueTreeSessionSavedData();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
        var key = new DialogueTreeService.SessionKey(player.getUUID(), villager.getUUID());
        var session = new DialogueTreeService.Session(UUID.randomUUID(), ResourceLocation.parse("fixture:missing_" + UUID.randomUUID()),
                "road", 10, Map.of("player", "Alex"));
        data.put(key, session);
        var storage = level.getServer().overworld().getDataStorage();
        storage.set("villagerretaliation_dialogue_trees", data);
        try {
            var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(level, player, villager);
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "unavailable content allows other quest topics");
            helper.assertValueEqual(data.get(key), session, "missing content keeps the original cursor for restoration");
            var node = new DialogueTreeDefinition.Node("road", java.util.List.of("Wait here."), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), false);
            var tree = new DialogueTreeDefinition(session.treeId(), "Road", "", DialogueEntryMetadata.EMPTY,
                    java.util.List.of(), java.util.List.of(), Map.of("road", node));
            com.jvn.villagerretaliation.dialogue.DialogueTreeResources.installTestTrees(level.getServer(), context.locale(), java.util.List.of(tree), java.util.Set.of());
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "a node without responses allows current quest topics");
            helper.assertValueEqual(data.get(key), session, "empty responses retain the saved cursor");
        } finally {
            storage.set("villagerretaliation_dialogue_trees", prior);
            com.jvn.villagerretaliation.dialogue.DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }
}
